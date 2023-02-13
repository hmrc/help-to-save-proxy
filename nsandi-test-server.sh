# password is "nspw"
openssl s_server -key conf/certificates/nsandiServerKey.pem -cert conf/certificates/nsandiServerCertificate.pem -accept 2443 -www -msg -state -debug
