import requests

url = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"

payload={
  'scope': 'GIGACHAT_API_PERS'
}
headers = {
  'Content-Type': 'application/x-www-form-urlencoded',
  'Accept': 'application/json',
  'RqUID': 'c2a2c374-9503-4fb1-9911-99dc628376a8',
  'Authorization': 'Basic MGFlN2NiMjYtM2RmYy00NDkzLWI0OWUtNjgzOTdkNTdhYzUzOmM2OTM0ZjFjLTZhODgtNGFkMS04Y2Q2LWQxMzkzMWU0ZjExNQ=='
}

response = requests.request("POST", url, headers=headers, data=payload, verify=False)

print(response.text)