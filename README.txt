*******************
Customer API
*******************

GET  /api/customers  - returns list of created customers

POST /api/customers  - creates new customer
Request:
{
 "cmd": {
 	 "id":"c01",
	 "firstName":"John",
	 "lastName":"Dell",
	 "email":"john.dell@gmail.com"	 
	 }
}

PUT /api/customers  - updates customer
Request:
{
 "cmd": {
 	 "id":"c01",
	 "firstName":"John",
	 "lastName":"Dell",
	 "email":"john.dell@gmail.com"	 
	 }
}

PUT /api/customers/status  - updates customer status
Request:
{
 "cmd": {
 	 "id":"c01",
	 "active":true
	 }
}

GET /api/customers/c01 - returns customer

*********************
Wallet API
*********************

GET /api/wallets/w01  - returns current wallet state

GET /api/wallets/w01/transactions  - returns all transactions for the given wallet wallet state

POST /api/wallets - creates/updates wallet
Request:
{
 "cmd": {	
     "id":"w01",
	 "customerId":"c01",
	 "name":"Wallet 1",
	 "amount":200.0,
	 "transactionType":"D",
	 "comment":""
	 }
}

POST /api/wallets/status - updates wallet status
Request:
{
 "cmd": {	
     "id":"w01",
	 "active":false
	 }
}

POST /api/wallets/balance - updates wallet balance
transactionType: D or W
{
 "cmd": {	
     "id":"w01",
	 "amount":50.0,
	 "transactionType":"W"
	 }
}

POST /api/wallets/transfer - transfer funds between wallets
{
 "fromWallet":"w01",
 "toWallet":"w02",
 "amount":100.0
}



