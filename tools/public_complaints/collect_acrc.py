"""Collect public, institution-published complaint excerpts, not private submissions.

Raw HTTP bodies remain in memory. Output is local-only, automatically redacted and
explicitly NOT privacy/relevance-reviewed. Nothing is imported into retrieval_item.
"""
import argparse
import hashlib
import json
import re
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import parse_qs, urlencode, urljoin, urlparse
from urllib.robotparser import RobotFileParser

import requests
from bs4 import BeautifulSoup

BASE = "https://www.acrc.go.kr"
BOARD = {"mid": "a10504010000", "bid": "1010"}
SOURCE = BASE + "/board.es?" + urlencode(BOARD)
START = re.compile(r"(?:신청\s*원인|민원\s*요지|신청\s*내용)\s*[:：]?")
END = re.compile(r"(?:[○□]\s*|\b[2-6]\s*[.．)]\s*)?(?:피신청인\s*(?:들|등)?\s*의?\s*(?:주장|의견)|사실\s*관계|관계\s*법령|판단\s*및\s*결론)")
EMAIL = re.compile(r"[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}")
PHONE = re.compile(r"(?<!\d)(?:0\d{1,2}[-.\s]?\d{3,4}[-.\s]?\d{4})(?!\d)")
RESIDENT_ID = re.compile(r"(?<!\d)\d{6}\s*[-–]\s*[1-8]\d{6}(?!\d)")
CASE_ID = re.compile(r"\b\d[A-Z]{2}-\d{4}-\d+\b|\b20\d{2}[가-힣]{1,4}\d{2,}\b")
PARTIAL_NAME = re.compile(r"[가-힣]{1,2}[○◯〇ＯO*＊]{1,3}")
# Conservative exclusion, rather than claiming a regex can fully anonymize text.
NAME_RISK = re.compile(r"(?<![가-힣])[가-힣]{2,4}\s*(?:씨|님)(?:[은는이가을를의,.)\s]|$)")


def normalize(text):
    return re.sub(r"\s+", " ", text).strip()


def redact(text):
    text = RESIDENT_ID.sub("[식별번호]", text)
    text = EMAIL.sub("[이메일]", text)
    text = PHONE.sub("[전화번호]", text)
    text = CASE_ID.sub("[사건번호]", text)
    text = PARTIAL_NAME.sub("[인물]", text)
    text = re.sub(r"(?<!\d)(?:19|20)\d{2}\s*[.년/-]\s*\d{1,2}\s*[.월/-]\s*\d{1,2}\s*일?\.?", "[날짜]", text)
    text = re.sub(r"(?<!\d)\d{1,3}\s*세\b", "[나이]", text)
    text = re.sub(r"(?<!\d)\d{2,3}\s*[가-힣]\s*\d{4}(?!\d)", "[차량번호]", text)
    text = re.sub(r"[가-힣○◯〇0-9]+(?:로|길)\s*\d+(?:-\d+)?(?:\s*번길\s*\d+)?", "[상세주소]", text)
    text = re.sub(r"\b\d{2,4}\s*(?:동|호)(?=[,.)\s]|$)", "[상세주소]", text)
    return normalize(text)


def extract(soup):
    content = soup.select_one(".board_view .contents")
    if content is None:
        return None, "missing_content"
    text = normalize(content.get_text(" ", strip=True))
    start = START.search(text)
    if start is None:
        return None, "no_public_complaint_section"
    rest = text[start.end():]
    end = END.search(rest)
    if end is None:
        return None, "unbounded_complaint_section"
    excerpt = rest[:end.start()]
    excerpt = re.sub(r"\s*[○□]?\s*\d\s*[.．)]\s*$", "", excerpt).strip()
    if len(excerpt) < 80:
        return None, "too_short"
    masked = redact(excerpt)
    if NAME_RISK.search(masked):
        return None, "possible_person_name"
    # Short research excerpts only; never store full decisions or answers.
    words = masked.split()
    truncated = len(words) > 160 or len(masked) > 1200
    masked = " ".join(words[:160])[:1200].strip()
    if any(p.search(masked) for p in (EMAIL, PHONE, RESIDENT_ID, CASE_ID)):
        return None, "residual_identifier"
    return {"masked_text": masked, "excerpt_truncated": truncated,
            "source_section": start.group().strip(" :：")}, None


class Collector:
    def __init__(self, output, count, delay):
        self.output, self.count, self.delay = output, count, delay
        output.mkdir(parents=True, exist_ok=True)
        self.session = requests.Session()
        self.session.headers["User-Agent"] = "CCAS-PublicCaseResearch/1.0 (public pages; sequential requests)"
        self.robot = RobotFileParser()
        response = self.session.get(BASE + "/robots.txt", timeout=30)
        response.raise_for_status()
        self.robot.parse(response.text.splitlines())
        self.rows = []
        self.events = []
        self.seen = set()
        self.hashes = set()
        for name, dest in (("complaints.jsonl", self.rows), ("audit.jsonl", self.events)):
            path = output / name
            if path.exists():
                dest.extend(json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line)
        self.seen.update(e["source_id"] for e in self.events)
        self.hashes.update(row["masked_text_sha256"] for row in self.rows)
        self.requests = 0

    def get(self, url):
        if urlparse(url).netloc != "www.acrc.go.kr" or not self.robot.can_fetch("*", url):
            raise RuntimeError("URL outside allowed public source")
        time.sleep(self.delay)
        response = self.session.get(url, timeout=40)
        self.requests += 1
        # Stop on throttling/access denial. Do not bypass login or challenge pages.
        response.raise_for_status()
        response.encoding = "utf-8"
        return BeautifulSoup(response.text, "html.parser")

    def append(self, name, value):
        with (self.output / name).open("a", encoding="utf-8") as stream:
            stream.write(json.dumps(value, ensure_ascii=False) + "\n")

    def checkpoint(self):
        report = {"dataset_id": "acrc-public-complaint-excerpts-500-v1", "source": SOURCE,
                  "requested_count": self.count, "collected_count": len(self.rows),
                  "unique_masked_texts": len(self.hashes), "synthetic": False,
                  "human_reviewed": False, "evaluation_ground_truth_available": False,
                  "category_counts": dict(Counter(r["source_category"] for r in self.rows)),
                  "excluded_counts": dict(Counter(e["status"] for e in self.events if e["status"] != "collected")),
                  "attempted_detail_pages": len(self.events), "requests_this_run": self.requests,
                  "updated_at": datetime.now(timezone.utc).isoformat(),
                  "selection": "Police category first; remaining board pages oldest-first (earlier resume attempts retained); HTML complaint section required; deduplicated",
                  "limitations": ["Institution-authored accounts of real complaints, not verbatim citizen submissions",
                                  "Automated redaction only; manual privacy and relevance review required",
                                  "No answer/order copied into query excerpt; no retrieval labels inferred",
                                  "Source licensing retained per record; local research staging only, no publication or ingestion"]}
        (self.output / "manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps({"collected": len(self.rows), "visited": len(self.events),
                          "categories": report["category_counts"]}, ensure_ascii=False), flush=True)

    def run(self):
        for category in ("C03", ""):
            page = 1
            step = 1
            if not category and len(self.rows) < self.count:
                first = self.get(SOURCE)
                last = first.select_one('.board_info .current b')
                if last is None:
                    raise RuntimeError('Cannot determine board page count')
                page = int(last.get_text(strip=True).replace(',', ''))
                step = -1
            while len(self.rows) < self.count and page > 0:
                listing = self.get(BASE + "/board.es?" + urlencode(BOARD | {"cg_code": category, "nPage": page}))
                entries = listing.select(".board_list tbody tr")
                links = []
                for entry in entries:
                    a = entry.select_one('a[href*="act=view"]')
                    if a:
                        links.append((entry, a))
                if not links:
                    break
                for entry, a in links:
                    url = urljoin(BASE, a["href"])
                    source_id = parse_qs(urlparse(url).query)["list_no"][0]
                    if source_id in self.seen:
                        continue
                    soup = self.get(url)
                    extracted, reason = extract(soup)
                    row = None
                    if extracted:
                        digest = hashlib.sha256(normalize(extracted["masked_text"]).encode()).hexdigest()
                        if digest in self.hashes:
                            reason = "duplicate_masked_text"
                        else:
                            title = redact(a.get_text(" ", strip=True))
                            if NAME_RISK.search(title):
                                reason = "possible_person_name_in_title"
                            else:
                                license_node = soup.select_one(".kogl_group")
                                category_node = entry.select_one('[aria-label="분류"]')
                                date_node = entry.select_one('[aria-label="등록일"]')
                                row = {"item_key": "acrc-" + source_id, "source_id": source_id,
                                       "source_url": BASE + "/board.es?" + urlencode(BOARD | {"act": "view", "list_no": source_id}),
                                       "source_organization": "국민권익위원회", "source_title_masked": title,
                                       "source_category": category_node.get_text(strip=True) if category_node else "UNKNOWN",
                                       "source_published_date": date_node.get_text(strip=True) if date_node else None,
                                       "retrieved_at": datetime.now(timezone.utc).isoformat(),
                                       "text_kind": "institution_published_real_complaint_excerpt", "synthetic": False,
                                       "original_citizen_submission": False, "human_reviewed": False,
                                       "privacy_review_status": "AUTOMATED_REDACTION_PENDING_MANUAL_REVIEW",
                                       "review_status": "REVIEW_REQUIRED", "maskingCompleted": False,
                                       "license_notice": normalize(license_node.get_text(" ", strip=True)) if license_node else "Not specified on page; review required",
                                       "masked_text_sha256": digest, **extracted}
                                self.rows.append(row)
                                self.hashes.add(digest)
                                self.append("complaints.jsonl", row)
                    event = {"source_id": source_id, "status": "collected" if row else reason}
                    self.events.append(event)
                    self.seen.add(source_id)
                    self.append("audit.jsonl", event)
                    if len(self.events) % 20 == 0:
                        self.checkpoint()
                    if len(self.rows) >= self.count:
                        break
                page += step
        self.checkpoint()
        if len(self.rows) != self.count:
            raise RuntimeError(f"Only {len(self.rows)} eligible excerpts; do not fabricate missing records")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path(".local-data/acrc-public-500"))
    parser.add_argument("--count", type=int, default=500)
    parser.add_argument("--delay", type=float, default=.35)
    args = parser.parse_args()
    if args.count < 1 or args.delay < .25:
        parser.error("positive count and delay >= .25 required")
    Collector(args.output, args.count, args.delay).run()
