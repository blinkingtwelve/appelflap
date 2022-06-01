# Intro

For other Appelflap installations to trust what they're about to stuff into their browser caches from some random peer who offered them something on the airwaves ("pssst, hey, want to swallow this cache package?") they verify that the package has been signed with a client certificate, which is signed by a deployment (=project) certificate, which in turn is signed by the Appelflap root CA which is built into Appelflap.
With the [django-appelflap-PKI package](https://github.com/blinkingtwelve/django-appelflap-PKI) users with the `appelflap_PKI.sign_appelflap_p2p_packages`  permission can acquire a deployment-signed package-signing certificate.

It's best to assign this permission only to users who are expected to actually connect to the web and spread updates, but it could be assigned to everyone, depending on project requirements. Technically with this permission the user could inject anything they want into other user's caches, eg replace the lesson video with something inappropriate, so generally it's better to give this permission just to people who have no interest in causing classroom mayhem, eg teachers.

In a nutshell, in terms of PKI responsibilities, the webapp needs to:

1. Get an unsigned certificate from Appelflap. If Appelflap returns a certificate chain of length 3, then the certificate has already been signed and no further action needs to be taken.
2. Post it to a backend server for signing. The body can be taken verbatim from the response acquired in the previous step. The backend server will return a signed certificate in a certificate chain.
3. Post that certificate chain verbatim to Appelflap.

Note that in the above steps the PWA did not need to handle any private key material or crypto operations.

Under certain circumstances it may be desirable to delete the certificate. This would be appropriate when a device changes hands permanently from a privileged user (say, a teacher) to another user (say, a student) who is not supposed to be able to spread Caches. A good place to check if whether the certificate needs to be created or deleted is on webapp login and logout.


# Operations - Entity: Certificate chain

## Acquiring the package signing certificate

### Request
```
GET /appelflap/ingeblikt/certchain
```

#### Request headers
- `Authorization` - see Authentication

#### Request body

Ignored.

### Response

#### Response status
- 200 on success
- 401 when authentication has failed

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Response body

The certificate chain in PEM format. If there wasn't a package signing certificate, one will be generated — this can cause a little hiccup on slower devices.

#### Response headers
- `Content-Type`: "application/x-pem-file"
- `X-Appelflap-Chain-Length`: A number, either 1 or 3. If the certificate is not signed, this will be 1. If the certificate is signed, this will be 3, and the body will contain the package signing certificate, deployment certificate, and Appelflap root certificate, in that order.


## Uploading the signed package signing certificate

### Request
```
PUT /appelflap/ingeblikt/certchain
```

#### Request headers
- `Authorization` - see Authentication
- `Content-Type`: "application/x-pem-file"

#### Request body

A PEM file containing 3 certificates:

1. New package signing certificate (with CommonName equal this instance's ID, and signed with the next certificate)
2. Deployment certificate (with CommonName equal to the Appelflap application ID, and signed with the next certificate)
3. Appelflap root certificate (will be the same as Appelflap's built-in copy)

### Response

#### Response status
- 200 on success
- 401 when authentication has failed
- 400 if the certificate does not pass muster

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Response body

If unsuccessful, contains hints on why the proposed chain does not pass muster (same as in status phrase).



## Deleting the package signing certificate

### Request
```
DELETE /appelflap/ingeblikt/certchain
```
#### Request headers
- `Authorization` - see Authentication

#### Request body

Ignored.

### Response

#### Status
- 200 on success
- 401 when authentication has failed

Generally, there will be an accompanying human-readable reason phrase in the response.

#### Body

Empty.