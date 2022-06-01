# Info blocks

Things worth knowing about Appelflap's state or Android's state, exposed over HTTP.


## WiFi info

### Request

```
GET /appelflap/info/wifi-info
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored

### Response body

A JSON document like so:

```json
{
  "network": {
    "state": "ENABLED",
    "supplicantstate": "COMPLETED",
    "ssid": "\"AndroidWifi\"",
    "ipaddress": "192.168.232.2",
    "ipaddress_raw": 48801984,
    "strength": 100
  }
}
```

- `wifistate` - `AP_MODE` if the device is in AP mode, or the name of one of the `WIFI_STATE_*` constants in https://developer.android.com/reference/android/net/wifi/WifiManager#EXTRA_WIFI_STATE
- `supplicantstate` - The name of one of the constants in https://developer.android.com/reference/android/net/wifi/SupplicantState#summary
- `strength` will be between 0 and 100
- `ssid` - Three cases:
  1. `null` if it could not be determined (for instance, this is the case in AP mode, or when wifi is disabled)
  2. if the SSID can be decoded as UTF-8, it will be returned surrounded by double quotation marks.
  3. If not, it is returned as a string of hex digits.

Note that in AP mode, ssid nor network strength can be determined.
Note that when not connected, ssid nor ip address nor network strength can be determined.


## Bonjour peer listing

These are peers that are more-or-less live on the LAN.

### Request

```
GET /appelflap/info/bonjour-peers
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored

### Response body

A JSON document like so:

```json
[
  {
    "id": 8888888,
    "friendly_id": "Ysva"
  },
  {
    "id": 6189619,
    "friendly_id": "Gday"
  },
  {
    "id": 5239691,
    "friendly_id": "Aw99"
  },
  {
    "id": 166375,
    "friendly_id": "3222"
  }
]
```

There is no defined order to the array elements.

The `id` field contains the numeric peer ID field, and the informational `friendly_id` field contains a base-55 representation generated using the symbol palette available from `moz-extension://appelflap/peerid.json` (when running inside Appelflap, otherwise consult `NodeID.kt`.)


## Storage info

Info about the main storage of the device.

### Request

```
GET /appelflap/info/storage-info
```

#### Request headers
- `Authorization` - see Authentication

#### Body

Ignored

### Response body

A JSON document like so:

```json
{
  "disksize": 2109456384,
  "diskfree": 1654304768
}
```

The unit of the amounts is byte.