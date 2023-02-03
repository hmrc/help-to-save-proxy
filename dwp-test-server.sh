# password is "dspw"
openssl s_server -key conf/certificates/dwpServerKey.pem -cert conf/certificates/dwpServerCertificate.pem -accept 1443 -www -msg -state -debug
