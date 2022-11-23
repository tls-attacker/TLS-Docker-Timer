Contains pre-generated certs to fill the cert-data docker volume to obtain identical results.
Note that there have been some modifications:
1) No EC key has been added to /db/ to fix an NSS issue with for duplicated cert alias
2) A policy file for botan has been added which can be set to allow RSA key exchange
