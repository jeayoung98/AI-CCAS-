"""Bounded retries for transient GMS failures; never expose credentials or response bodies."""
import os
import time
import requests


def chat(payload,timeout=90):
    for attempt in range(3):
        try:
            response=requests.post(os.environ['OPENAI_BASE_URL'].rstrip('/')+'/chat/completions',
                headers={'Authorization':'Bearer '+os.environ['OPENAI_API_KEY']},json=payload,timeout=timeout)
        except (requests.Timeout,requests.ConnectionError):
            if attempt==2:raise RuntimeError('GMS transport failure; details omitted') from None
            time.sleep(2**(attempt+1));continue
        if response.ok:return response.json()
        if response.status_code not in (429,500,502,503,504) or attempt==2:
            raise RuntimeError(f'GMS HTTP {response.status_code}; response omitted')
        try: delay=float(response.headers.get('Retry-After',2**(attempt+1)))
        except (ValueError,TypeError):delay=2**(attempt+1)
        time.sleep(max(1,min(30,delay)))
    raise AssertionError('Unreachable')
