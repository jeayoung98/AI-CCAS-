import unittest
from unittest.mock import Mock,patch
from gms_request import chat

class GmsRequestTest(unittest.TestCase):
    @patch.dict('os.environ',{'OPENAI_BASE_URL':'https://example.invalid','OPENAI_API_KEY':'test-value'})
    @patch('gms_request.time.sleep')
    @patch('gms_request.requests.post')
    def test_transient_retry_is_bounded_and_success_is_returned(self,post,sleep):
        failed=Mock(ok=False,status_code=429,headers={'Retry-After':'9999'})
        good=Mock(ok=True);good.json.return_value={'choices':[]}
        post.side_effect=[failed,good]
        self.assertEqual(chat({}),{'choices':[]});self.assertEqual(post.call_count,2)
        sleep.assert_called_once_with(30)

    @patch.dict('os.environ',{'OPENAI_BASE_URL':'https://example.invalid','OPENAI_API_KEY':'test-value'})
    @patch('gms_request.time.sleep')
    @patch('gms_request.requests.post')
    def test_permanent_failure_does_not_retry_or_expose_body(self,post,sleep):
        post.return_value=Mock(ok=False,status_code=401,text='sensitive response')
        with self.assertRaisesRegex(RuntimeError,'HTTP 401; response omitted'):chat({})
        sleep.assert_not_called();self.assertEqual(post.call_count,1)
