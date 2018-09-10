
[ ![Download](https://api.bintray.com/packages/hmrc/releases/help-to-save-proxy/images/download.svg) ](https://bintray.com/hmrc/releases/help-to-save-proxy/_latestVersion)

## Help to Save Proxy 

## How to run

Runs on port 7004 when started locally by the service manager.

sbt "run 7004"

## Endpoints

## Main Public API

| Path                                                          | Supported Methods | Description  |
| --------------------------------------------------------------| ------------------| ------------ |
|`/help-to-save-proxy/create-account`                           |        GET        | Submits a request to NS&I to create a HTS account|
|`/help-to-save-proxy/update-email`                             |        GET        | Submits a request to NS&I to update the user's email address|

License
---

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
