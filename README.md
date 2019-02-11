help-to-save-proxy
==================

This service allows Help to Save to make requests to services outside of MDTP. Used to make requests to NS&I and DWP.

Table of Contents
=================

* [About Help to Save](#about-help-to-save)
* [Running and Testing](#running-and-testing)
   * [Running](#running)
   * [Unit tests](#unit-tests)
* [Endpoints](#endpoints)
* [License](#license)

About Help to Save
==================
Please click [here](https://github.com/hmrc/help-to-save#about-help-to-save) for more information.

Running and Testing
===================

Running
-------

Run `sbt run` on the terminal to start the service. The service runs on port 7005 by default.  

Unit tests                                              
----------                                              
Run `sbt test` on the terminal to run the unit tests.   


Endpoints
=========

| Path                                                          | Method            | Description  |
| --------------------------------------------------------------| ------------------| -------------|
|`/help-to-save-proxy/create-account`                           |        POST       | Creates a HTS account|
|`/help-to-save-proxy/update-email`                             |        PUT        | Update the user's email address on their HTS account|
|`/help-to-save-proxy/nsi-services/*resource`                   |        GET        | Submits a request to NS&I to query some resource (e.g. account info or transaction data)|
|`/help-to-save-proxy/uc-claimant-check`                        |        GET        | Checks if a person is a UC claimant and if they are whether they're earning above a given threshold|


License
=======

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
