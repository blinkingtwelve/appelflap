# Preliminaries

## Cache handling
1. The unit of operation is a Cache object. Appelflap can pack up Cache objects for publishing, or inject Cache objects it has acquired over the air from other Appelflap instances.
2. The identity of a cache is `(web-origin, cache-name)`, the identity of a canned cache object is `(web-origin, cache-name, version-number)`
2. Published caches have version numbers. Appelflap uses these to decide whether something is an update worth retrieving and injecting. A version number is a whole number between 0 and 2^63 -1.
2. Only one version of a cache is published (offered to other Appelflap instances) at any time.
3. There is an HTTP API exposed on the localhost interface for the webapp to address. The port number is dynamic and is available to the browser and serviceworker contexts by reading an extension URI. Before any operation, the webapp client always needs to retrieve the current port number. See [Determining endpoint properties](determining-endpoint.md) for more information.
4. To pack up and distribute a cache, you need a certificate signed by the deployment certificate, which in turn is signed by the Appelflap root CA. See the appropriate API topics and [the django-appelflap-PKI Django app](https://github.com/blinkingtwelve/django-appelflap-PKI) for more information.


## Authentication
The webapp makes requests to Appelflap using an Appelflap-injected port number, and so can assume that whatever has the power to inject that port number is to be trusted.

The converse is a bit more involved, since any app can discover the Appelflap HTTP server port and start making requests (on the localhost interface this would be limited to apps on the same apparatus, but still).
Upon startup Appelflap injects a randomly generated shared secret in a similar fashion to the way it injects the port number, see [Determining endpoint properties](determining-endpoint.md). This shared secret is to be used with every request.

# API topics

## Cache related
- [Cache publication](cache-publication.md)
- [Update subscription](update-subscription.md)
- [Cache injection](cache-injection.md)
- [Certificate signing](certificate-signing.md)

## Large object storage
- [Large object storage facility](eikels.md)

## The rest
- [Make things do things](actions.md)
- [Assorted info blocks](info.md)
- [Configure Appelflap settings](settings.md)
- [Manage downloads](downloads.md)