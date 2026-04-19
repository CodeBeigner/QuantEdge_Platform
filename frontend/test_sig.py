import hashlib
import hmac
import time

method = 'GET'
timestamp = str(int(time.time()))
path = '/v2/wallet/balances'
query_string = ''
payload = ''

signature_data = method + timestamp + path + query_string + payload
print(signature_data)
