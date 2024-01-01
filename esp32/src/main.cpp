/*
 * MIT License
 *
 * Copyright (C) 2023  <Wolfgang Kampichler>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

/**
 *  @file    main.cpp
 *  @author  Wolfgang Kampichler (DEC112)
 *  @date    10-2023
 *  @version 1.1
 *
 *  @brief ESP32 BLE-SENML Gateway (up to 4 BLEServer)
 */

/******************************************************************* INCLUDE */

#include <Arduino.h>
#include <BLEDevice.h>
#include <HTTPClient.h>
#include <WiFi.h>
#include <WebServer.h>
#include <AutoConnect.h>
#include <AutoConnectFS.h>
#include <esp_task_wdt.h>
#include <Preferences.h>

#include "json.h"

/******************************************************************* DEFINE */

#define ELEMENT_SIZE 512
#define DATA_SIZE 64
#define URN_SIZE 48
#define MAC_SIZE 24
#define LOC_SIZE 12
#define MAX_DEVICE 4
#define MAX_POOL 10
#define MAX_REDIR 8
#define MAX_ATTEMPTS 5
#define NO_INDEX -1

#define WDT_TIMEOUT 600

// adjust this part if necessary
#define DEV_NAME "DEC112-BLE-Client"
#define MAC_1 "68:72:c3:eb:8e:a9"
#define MAC_2 "e6:ea:13:f5:11:3b"
#define MAC_3 "fa:45:e3:78:45:ad"
#define MAC_4 "cc:e0:e7:20:43:85"
#define URL_PREFIX "https://"
#define GMT_OFF_SEC 3600
#define DLT_OFF_SEC 0
//

enum BLEState { D_DISCONNECTED, D_SCANNED, D_CONNECTING, D_CONNECTED };

typedef struct s_data {
  long double tm;
  int temp;
  bool f_temp;
  int bat;
  bool f_bat;
  int mov;
  bool f_mov;
  int btn;
  bool f_btn;
  bool valid;
} s_data;

typedef struct s_device {
  int state;
  int index;
  char mac[MAC_SIZE];
  char id[DATA_SIZE];
  char url0[DATA_SIZE];
  char url[DATA_SIZE];
  BLEAdvertisedDevice *pDevice;
  BLEClient *pClient;
  BLERemoteCharacteristic *tempCharacteristic;
  BLERemoteCharacteristic *batCharacteristic;
  BLERemoteCharacteristic *movCharacteristic;
  BLERemoteCharacteristic *btnCharacteristic;
  s_data data[MAX_POOL];
} s_device;

typedef struct {
  char lat[LOC_SIZE];
  char lon[LOC_SIZE];
  int accuracy = 40000;
} location_t;

typedef struct {
  const char* zone;
  const char* ntpServer;
  int8_t      tzoff;
} Timezone_t;

static const Timezone_t TZ[] = {
  { "Europe/London", "europe.pool.ntp.org", 0 },
  { "Europe/Berlin", "europe.pool.ntp.org", 1 },
  { "Europe/Helsinki", "europe.pool.ntp.org", 2 },
  { "Europe/Moscow", "europe.pool.ntp.org", 3 },
  { "Asia/Dubai", "asia.pool.ntp.org", 4 },
  { "Asia/Karachi", "asia.pool.ntp.org", 5 },
  { "Asia/Dhaka", "asia.pool.ntp.org", 6 },
  { "Asia/Jakarta", "asia.pool.ntp.org", 7 },
  { "Asia/Manila", "asia.pool.ntp.org", 8 },
  { "Asia/Tokyo", "asia.pool.ntp.org", 9 },
  { "Australia/Brisbane", "oceania.pool.ntp.org", 10 },
  { "Pacific/Noumea", "oceania.pool.ntp.org", 11 },
  { "Pacific/Auckland", "oceania.pool.ntp.org", 12 },
  { "Atlantic/Azores", "europe.pool.ntp.org", -1 },
  { "America/Noronha", "south-america.pool.ntp.org", -2 },
  { "America/Araguaina", "south-america.pool.ntp.org", -3 },
  { "America/Blanc-Sablon", "north-america.pool.ntp.org", -4},
  { "America/New_York", "north-america.pool.ntp.org", -5 },
  { "America/Chicago", "north-america.pool.ntp.org", -6 },
  { "America/Denver", "north-america.pool.ntp.org", -7 },
  { "America/Los_Angeles", "north-america.pool.ntp.org", -8 },
  { "America/Anchorage", "north-america.pool.ntp.org", -9 },
  { "Pacific/Honolulu", "north-america.pool.ntp.org", -10 },
  { "Pacific/Samoa", "oceania.pool.ntp.org", -11 }
};

const char AUX_DEC4IOT[] PROGMEM = R"raw(
[
    {
        "title": "DEC4IOT",
        "uri": "/dec4iot",
        "menu": true,
        "element": [
            {
                "name": "caption1",
                "type": "ACText",
                "value": "Local time zone:",
                "style": "font-family:Arial;font-weight:bold;text-align:center;margin-bottom:10px;color:DarkSlateBlue"
            },
            {
                "name": "newline1",
                "type": "ACElement",
                "value": "<br>"
            },
            {
                "name": "timezone",
                "type": "ACSelect",
                "label": "Timezone",
                "option": [],
                "selected": 2
            },
            {
                "name": "newline2",
                "type": "ACElement",
                "value": "<br>"
            },
            {
                "name": "caption2",
                "type": "ACText",
                "value": "BLE server MAC (Puck.js):",
                "style": "font-family:Arial;font-weight:bold;text-align:center;margin-bottom:10px;color:DarkSlateBlue"
            },
            {
                "name": "newline3",
                "type": "ACElement",
                "value": "<br>"
            },
            {
                "name": "mac1",
                "type": "ACInput",
                "label": "MAC 1",
                "pattern": "^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$"
            },
            {
                "name": "mac2",
                "type": "ACInput",
                "label": "MAC 2",
                "pattern": "^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$"
            },
            {
                "name": "mac3",
                "type": "ACInput",
                "label": "MAC 3",
                "pattern": "^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$"
            },
            {
                "name": "mac4",
                "type": "ACInput",
                "label": "MAC 4",
                "pattern": "^([0-9A-Fa-f]{2}[:]){5}([0-9A-Fa-f]{2})$"
            },
            {
                "name": "newline4",
                "type": "ACElement",
                "value": "<br>"
            },
            {
                "name": "save",
                "type": "ACSubmit",
                "value": "Save",
                "uri": "/save"
            },
            {
                "name": "cancel",
                "type": "ACSubmit",
                "value": "Cancel",
                "uri": "/_ac"
            }
        ]
    },
    {
        "title": "DEC4IOT INFO",
        "uri": "/save",
        "menu": false,
        "element": [
            {
                "name": "url",
                "type": "ACText",
                "style": "font-family:Arial;font-weight:bold;text-align:center;margin-bottom:10px;color:DarkSlateBlue"
            },
            {
                "name": "newline",
                "type": "ACElement",
                "value": "<br>"
            },
            {
                "name": "home",
                "type": "ACSubmit",
                "value": "Home",
                "uri": "/_ac"
            }
        ]
    }
]
)raw";

/******************************************************************* GLOBALS */

const char *mozillaApi =
    "https://location.services.mozilla.com/v1/geolocate?key=test";

int TZindex = 0;

char myMacs[MAX_DEVICE][MAC_SIZE] = {MAC_1, MAC_2, MAC_3, MAC_4};

static location_t location;
static s_device myDev[MAX_DEVICE];
static boolean doScan = false;
static boolean isConfigured = false;
static WiFiClientSecure *client;
static HTTPClient http;

// AutoConnect
AutoConnect Portal;
AutoConnectConfig Config;

// EEPROM storage
Preferences pref;

// BLE Service
BLEScan *pBLEScan = NULL;

// Service UUID
static BLEUUID serviceUUID("34defd2c-c8fe-b18e-9a70-591970cba32b");

// BLE Characteristics
// Temperature Celsius Characteristic
static BLEUUID tempCharacteristicUUID("2a6e");
// Battery Characteristic
static BLEUUID batCharacteristicUUID("2a19");
// Button Characteristic
static BLEUUID btnCharacteristicUUID("2ae2");
// Movement Characteristic
static BLEUUID movCharacteristicUUID("2c01");

/***************************************************************** FUNCTIONS */

/// @brief  load MAC addresses from preferences (EEPROM)
/// @return int (1 if success; otherwise 0)
int getMACs(void) {

  size_t len = 0;
  int ret = 0;

  char tmp[MAC_SIZE];

  len = pref.getString("mac0", tmp, MAC_SIZE);
  if (len > 0) {
    memcpy(myMacs[0], (const char*)tmp, MAC_SIZE);
    Serial.printf("MAC0: %s\n", myMacs[0]);
    len = 0;
    ret = 1;
  }
  len = pref.getString("mac1", tmp, MAC_SIZE);
  if (len > 0) {
    memcpy(myMacs[1], (const char*)tmp, MAC_SIZE);
    Serial.printf("MAC1: %s\n", myMacs[1]);
    len = 0;
    ret = 1;
  }
  len = pref.getString("mac2", tmp, MAC_SIZE);
  if (len > 0) {
    memcpy(myMacs[2], (const char*)tmp, MAC_SIZE);
    Serial.printf("MAC2: %s\n", myMacs[2]);
    len = 0;
    ret = 1;
  }
  len = pref.getString("mac3", tmp, MAC_SIZE);
  if (len > 0) {
    memcpy(myMacs[3], (const char*)tmp, MAC_SIZE);
    Serial.printf("MAC3: %s\n", myMacs[3]);
    len = 0;
    ret = 1;
  }

  return ret;
}

/// @brief  loadOn event handler
/// @return string
String loadOn(AutoConnectAux& aux, PageArgument& args) {

  AutoConnectSelect& tz = aux.getElement<AutoConnectSelect>("timezone");
  
  for (uint8_t n = 0; n < sizeof(TZ) / sizeof(Timezone_t); n++) {
    tz.add(String(TZ[n].zone));
  }

  return String("");
}

/// @brief  saveOn event handler
/// @return string
String saveOn(AutoConnectAux& aux, PageArgument& args) {

  AutoConnectAux* page = Portal.aux(Portal.where());
  
  AutoConnectInput& mac1 = page->getElement<AutoConnectInput>("mac1");
  AutoConnectInput& mac2 = page->getElement<AutoConnectInput>("mac2");
  AutoConnectInput& mac3 = page->getElement<AutoConnectInput>("mac3");
  AutoConnectInput& mac4 = page->getElement<AutoConnectInput>("mac4");

  if (mac1.value) {
    memcpy(myMacs[0], (const char*)mac1.value.c_str(), MAC_SIZE);
    pref.putString("mac0", myMacs[0]);
  }
  if (mac2.value) {
    memcpy(myMacs[1], (const char*)mac2.value.c_str(), MAC_SIZE);
    pref.putString("mac1", myMacs[1]);
  }
  if (mac2.value) {
    memcpy(myMacs[2], (const char*)mac3.value.c_str(), MAC_SIZE);
    pref.putString("mac2", myMacs[2]);
  }
  if (mac3.value) {
    memcpy(myMacs[3], (const char*)mac4.value.c_str(), MAC_SIZE);
    pref.putString("mac3", myMacs[3]);
  }

  AutoConnectSelect& tz = page->getElement<AutoConnectSelect>("timezone");

  String selected = tz.value();

  for (uint8_t n = 0; n < sizeof(TZ) / sizeof(Timezone_t); n++) {
    String  tzName = String(TZ[n].zone);
    if (selected.equalsIgnoreCase(tzName)) {
      Serial.println("Time zone: " + selected);
      Serial.println("ntp server: " + String(TZ[n].ntpServer));
      TZindex = n;
      configTime(TZ[TZindex].tzoff * GMT_OFF_SEC, DLT_OFF_SEC, TZ[TZindex].ntpServer);
      break;
    }
  }

  AutoConnectText&  url = aux.getElement<AutoConnectText>("url");
  url.value = String("http://") + WiFi.localIP().toString() + String("/dec4iot"); ;

  pBLEScan->setActiveScan(true);
  isConfigured = true;
  
  return String("");
}

/// @brief  onConnect event handler
/// @return void
void onConnect(IPAddress& ipaddr) {
  Serial.print("WiFi connected with ");
  Serial.print(WiFi.SSID());
  Serial.print(", IP:");
  Serial.println(ipaddr.toString());
}

/// @brief  converts MAC address to string
/// @return string
String mac_to_string(uint8_t *macAddress) {
  char macStr[18] = {0};
  sprintf(macStr, "%02X:%02X:%02X:%02X:%02X:%02X", macAddress[0], macAddress[1],
          macAddress[2], macAddress[3], macAddress[4], macAddress[5]);

  return String(macStr);
}

/// @brief  looks for surrounding SSIDs
/// @return JSON including WiFi information
String get_surrounding_wifi_json() {
  String wifiArray = "[\n";
  int8_t numWifi = WiFi.scanNetworks();

  for (uint8_t i = 0; i < numWifi; i++) { // numWifi; i++) {
    wifiArray += "{\"macAddress\":\"" + mac_to_string(WiFi.BSSID(i)) + "\",";
    wifiArray += "\"signalStrength\":" + String(WiFi.RSSI(i)) + ",";
    wifiArray += "\"channel\":" + String(WiFi.channel(i)) + "}";
    if (i < (numWifi - 1)) {
      wifiArray += ",\n";
    }
  }
  WiFi.scanDelete();
  wifiArray += "]";

  return wifiArray;
}

/// @brief  gets location via Mozilla API
/// @return location object
location_t get_location() {
  location_t location;

  if (WiFi.status() == WL_CONNECTED) {

    client->setInsecure();

    http.begin(*client, mozillaApi);

    http.addHeader("Content-Type", "application/json");
    http.addHeader("User-Agent", "ESP32");

    String body = "{\"wifiAccessPoints\":" + get_surrounding_wifi_json() + "}";

    Serial.printf("JSON:%s\n", body.c_str());

    int httpResponseCode = http.POST(body);
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);

    // httpCode will be negative on error
    if (httpResponseCode > 0) {
      if (httpResponseCode == HTTP_CODE_OK) {
        String response = http.getString();
        Serial.println(response);

        int index = response.indexOf("\"lat\":");
        if (index != -1) {
          String tempStr = response.substring(index);
          tempStr =
              tempStr.substring(tempStr.indexOf(":") + 1, tempStr.indexOf(","));
          snprintf(location.lat, LOC_SIZE, "%s", tempStr);
          Serial.printf("Lat: %s\n", location.lat);
        }

        index = response.indexOf("\"lng\":");
        if (index != -1) {
          String tempStr = response.substring(index);
          tempStr =
              tempStr.substring(tempStr.indexOf(":") + 1, tempStr.indexOf("}"));
          snprintf(location.lon, LOC_SIZE, "%s", tempStr);
          Serial.printf("Lon: %s\n", location.lon);
        }

        index = response.indexOf("\"accuracy\":");
        if (index != -1) {
          String tempStr = response.substring(index);
          tempStr = tempStr.substring(tempStr.indexOf(":") + 1,
                                      tempStr.indexOf("\n"));
          location.accuracy = tempStr.toFloat();
          Serial.println("Accuracy: " + String(location.accuracy) + "\n");
        }
      }
    } else {
      Serial.printf("[HTTPS] POST... failed, error: %s\n",
                    http.errorToString(httpResponseCode).c_str());
    }

    // Free resources
    http.end();
    client->stop();
  }
  return location;
}

/// @brief  resets device data
/// @return
void reset_data(s_data *d) {
  d->valid = false;
  d->bat = 0;
  d->f_bat = false;
  d->temp = 0;
  d->f_temp = false;
  d->mov = 0;
  d->f_mov = false;
  d->btn = 0;
  d->f_btn = false;
  d->tm = 0;

  return;
}

/// @brief  resets device with index i
/// @return
void reset_device(int i) {
  if (i < 0 || i > MAX_DEVICE - 1) {
    return;
  }
  myDev[i].state = D_DISCONNECTED;
  sprintf(myDev[i].mac, "%s", "00:00:00:00:00:00");
  myDev[i].pClient = NULL;
  myDev[i].pDevice = NULL;
  myDev[i].batCharacteristic = NULL;
  myDev[i].btnCharacteristic = NULL;
  myDev[i].movCharacteristic = NULL;
  myDev[i].tempCharacteristic = NULL;
  myDev[i].index = 0;
  for (int j = 0; j < MAX_POOL; j++) {
    s_data d = myDev[i].data[j];
    reset_data(&d);
  }
  return;
}

/// @brief  resets all devices
/// @return
void reset_devices(void) {
  for (int i = 0; i < MAX_DEVICE; i++) {
    reset_device(i);
  }
  return;
}

/// @brief  checks sensor dataset
/// @return bool (true if dataset is complete)
bool check_data(s_data *d) {
  return (d->f_bat & d->f_btn & d->f_mov & d->f_temp & d->valid);
}

/// @brief  stores battery value to dataset
/// @return
void set_data_bat(s_data *d, int val) {
  if (d->f_bat == false) {
    d->bat = val;
    d->f_bat = true;
    if (d->f_btn & d->f_mov & d->f_temp) {
      d->valid = true;
    }
  }
  return;
}

/// @brief  stores temperature value to dataset
void set_data_temp(s_data *d, int val) {
  if (d->f_temp == false) {
    d->temp = val;
    d->f_temp = true;
    if (d->f_bat & d->f_btn & d->f_mov) {
      d->valid = true;
    }
  }
  return;
}

/// @brief  stores movement value to dataset
/// @return
void set_data_mov(s_data *d, int val) {
  if (d->f_mov == false) {
    d->mov = val;
    d->f_mov = true;
    if (d->f_bat & d->f_btn & d->f_temp) {
      d->valid = true;
    }
  }
  return;
}

/// @brief  stores button value to dataset
/// @return
void set_data_btn(s_data *d, int val) {
  if (d->f_btn == false) {
    d->btn = val;
    d->f_btn = true;
    if (d->f_bat & d->f_mov & d->f_temp) {
      d->valid = true;
    }
  }
  return;
}

/// @brief  stores id string to dataset
/// @return
void set_data_id(s_device *d, char *id) {
  if (id == NULL) {
    return;
  }
  if (strlen(id) > DATA_SIZE - 1) {
    return;
  }
  sprintf(d->id, "%s", id);

  return;
}

/// @brief  stores url string to dataset
/// @return
void set_data_url(s_device *d, char *url) {
  if (url == NULL) {
    return;
  }
  if (strlen(url) > DATA_SIZE - 1) {
    return;
  }
  sprintf(d->url, "%s", url);

  return;
}

/// @brief  stores url and id string to dataset
/// @return
void set_characteristic(s_device *d, const char *s) {
  size_t len, base = 0;

  if (s == NULL) {
    return;
  }
  if ((strlen(s) > DATA_SIZE - 1) || (strlen(s) < 5)) {
    return;
  }

  char *tmp = (char *)s;
  int i = 0;

  while (1) {
    if ((tmp[i + 0] == 'i') && (tmp[i + 1] == '=')) {
      base = i + 2;
    }
    if (tmp[i] == ';') {
      len = i - base;
      break;
    }
    i++;
    if (i > DATA_SIZE) {
      *d->id = '\0';
      *d->url = '\0';
      return;
    }
  }

  tmp = (char *)s + base;
  snprintf(d->id, len + 1, "%s", tmp);

  tmp = (char *)s + base + len + 3;
  len = strlen(s) - len;

  snprintf(d->url, len + strlen(URL_PREFIX), URL_PREFIX "%s", tmp);
  snprintf(d->url0, len + strlen(URL_PREFIX), URL_PREFIX "%s", tmp);

  return;
}

/// @brief  check if the device MAC address is known
/// @return bool (true if mac found)
bool has_mac(const char *mac) {
  if (mac == NULL) {
    return false;
  }
  for (int i = 0; i < MAX_DEVICE; i++) {
    if (strcmp(myMacs[i], mac) == 0) {
      return true;
    }
  }
  return false;
}

/// @brief  reformats MAC address; removes colons and inserts 'ffff'
/// @return
void set_smac(char *mac) {
  if (mac == NULL) {
    return;
  }
  char *cur = mac;
  size_t len = strlen(mac);
  int p = 0;

  // remove ':' from left
  while (1) {
    if (p < 8)
      *cur = mac[p];
    if (p == len)
      break;
    else if (*cur != ':')
      cur++;
    p++;
  }

  p = len;
  cur = &mac[len];

  // remove ':' from right
  while (1) {
    if (p > 8)
      *cur = mac[p];
    if (p == 0)
      break;
    else if (mac[p] != ':')
      cur--;
    p--;
  }

  p = 0;
  cur = mac;

  // insert'ffff'
  while (1) {
    if (p > 5 && p < 10)
      *cur = 'f';
    if (p > 9)
      *cur = *(cur + 1);
    if (p == len)
      break;
    p++;
    cur++;
  }
  *cur = '\0';

  return;
}

/// @brief  returns first device index of state s
/// @return int (-1 if not found)
int index_by_state(int s) {
  for (int i = 0; i < MAX_DEVICE; i++) {
    if (myDev[i].state == s) {
      return i;
    }
  }
  return NO_INDEX;
}

/// @brief  returns first device index of BLEClient c
/// @return int (-1 if not found)
int index_by_client(BLEClient *c) {
  for (int i = 0; i < MAX_DEVICE; i++) {
    if (myDev[i].pClient == c) {
      return i;
    }
  }
  return NO_INDEX;
}

/// @brief  returns first device index of MAC m
/// @return int (-1 if not found)
int index_by_mac(const char *m) {
  for (int i = 0; i < MAX_DEVICE; i++) {
    if (strcmp(myDev[i].mac, m) == 0) {
      return i;
    }
  }
  return NO_INDEX;
}

/// @brief  returns current (same timestamp and incomplete data) or next index
/// @return int (< MAX_POOL)
int next_index(s_device *device, long double tm) {
  s_data *d = NULL;

  for (int i = 0; i < MAX_POOL; i++) {
    d = &(device->data[i]);
    // if received at almost the same time it may be part of a dataset
    if (abs((long)(tm - d->tm)) < 3) {
      if (d->valid) {
        // this dataset is complete ... get a new index
        continue;
      } else {
        // this dataset is incomplete ... return index
        return i;
      }
    }
    // there is no timestamp ... return the next avaliable index
    if (!d->valid && d->tm == 0) {
      // store timestamp
      d->tm = tm;
      return i;
    }
  }
  // something went wrong ... clean up the buffer
  for (int j = 0; j < MAX_POOL; j++) {
    s_data d = device->data[j];
    reset_data(&d);
  }
  // store timestamp
  d = &(device->data[0]);
  d->tm = tm;

  return 0;
}

/// @brief gets local epoch time
/// @return time_t struct
unsigned long get_epoch_time() {
  time_t now;
  struct tm timeinfo;
  if (!getLocalTime(&timeinfo)) {
    return 0;
  }
  time(&now);

  return now;
}

/// @brief sends a SenML JSON object to a webservice
/// @return
void send_json(s_device *dev, s_data *mydata) {
  jsonb b;
  jsonbcode ret;

  char buf[ELEMENT_SIZE];
  char urn[URN_SIZE];
  char smac[MAC_SIZE];

  strcpy(smac, dev->mac);
  set_smac(smac);

  snprintf(urn, URN_SIZE, "urn:dev:mac:%s:", smac);

  jsonb_init(&b);
  {
    jsonb_array(&b, buf, ELEMENT_SIZE);
    {
      {
        jsonb_object(&b, buf, ELEMENT_SIZE);
        jsonb_key(&b, buf, ELEMENT_SIZE, "bn", strlen("bn"));
        jsonb_string(&b, buf, ELEMENT_SIZE, urn, strlen(urn));
        jsonb_key(&b, buf, ELEMENT_SIZE, "bt", strlen("bt"));
        jsonb_float(&b, buf, ELEMENT_SIZE, mydata->tm);
        jsonb_key(&b, buf, ELEMENT_SIZE, "n", strlen("n"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "batt", strlen("batt"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "u", strlen("u"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "%EL", strlen("%EL"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "v", strlen("v"));
        jsonb_number(&b, buf, ELEMENT_SIZE, mydata->bat);
        jsonb_object_pop(&b, buf, ELEMENT_SIZE);
      }
      {
        jsonb_object(&b, buf, ELEMENT_SIZE);
        jsonb_key(&b, buf, ELEMENT_SIZE, "n", strlen("n"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "id", strlen("id"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "vs", strlen("vs"));
        jsonb_string(&b, buf, ELEMENT_SIZE, dev->id, strlen(dev->id));
        jsonb_object_pop(&b, buf, ELEMENT_SIZE);
      }
      {
        jsonb_object(&b, buf, ELEMENT_SIZE);
        // HACK >>>
        jsonb_key(&b, buf, ELEMENT_SIZE, "n", strlen("n"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "lat", strlen("lat"));
        // <<<
        jsonb_key(&b, buf, ELEMENT_SIZE, "u", strlen("u"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "lat", strlen("lat"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "v", strlen("v"));
        jsonb_number(&b, buf, ELEMENT_SIZE, strtod(location.lat, NULL));
        jsonb_object_pop(&b, buf, ELEMENT_SIZE);
      }
      {
        jsonb_object(&b, buf, ELEMENT_SIZE);
        // HACK >>>
        jsonb_key(&b, buf, ELEMENT_SIZE, "n", strlen("n"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "lon", strlen("lon"));
        // <<<
        jsonb_key(&b, buf, ELEMENT_SIZE, "u", strlen("u"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "lon", strlen("lon"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "v", strlen("v"));
        jsonb_number(&b, buf, ELEMENT_SIZE, strtod(location.lon, NULL));
        jsonb_object_pop(&b, buf, ELEMENT_SIZE);
      }
      {
        jsonb_object(&b, buf, ELEMENT_SIZE);
        jsonb_key(&b, buf, ELEMENT_SIZE, "n", strlen("n"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "temp", strlen("temp"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "u", strlen("u"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "Cel", strlen("Cel"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "v", strlen("v"));
        jsonb_number(&b, buf, ELEMENT_SIZE, mydata->temp);
        jsonb_object_pop(&b, buf, ELEMENT_SIZE);
      }
      {
        jsonb_object(&b, buf, ELEMENT_SIZE);
        jsonb_key(&b, buf, ELEMENT_SIZE, "n", strlen("n"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "move", strlen("move"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "vb", strlen("vb"));
        jsonb_bool(&b, buf, ELEMENT_SIZE, mydata->mov);
        jsonb_object_pop(&b, buf, ELEMENT_SIZE);
      }
      {
        jsonb_object(&b, buf, ELEMENT_SIZE);
        jsonb_key(&b, buf, ELEMENT_SIZE, "n", strlen("n"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "button", strlen("button"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "vb", strlen("vb"));
        jsonb_bool(&b, buf, ELEMENT_SIZE, mydata->btn);
        jsonb_object_pop(&b, buf, ELEMENT_SIZE);
      }
    }
    jsonb_array_pop(&b, buf, ELEMENT_SIZE);
  }
  Serial.printf("JSON:%s\n", buf);

  if (WiFi.status() == WL_CONNECTED) {
    char *url = NULL;
    const char *headerKeys[] = {"Location"};
    const size_t headerKeysCount = sizeof(headerKeys) / sizeof(headerKeys[0]);
    String httpRequestData = buf;
    int httpResponseCode = 0;
    int j = 0;
    int k = 0;

    client->setInsecure();
    while (1) {
      http.collectHeaders(headerKeys, headerKeysCount);
      Serial.printf("HTTP URL: %s\n", dev->url);
      http.begin(*client, dev->url);

      http.addHeader("Content-Type", "application/json");
      http.addHeader("User-Agent", "ESP32");

      httpResponseCode = http.POST(httpRequestData);
      Serial.print("HTTP Response code: ");
      Serial.println(httpResponseCode);
      // check for redirect response
      if ((httpResponseCode == HTTP_CODE_MOVED_PERMANENTLY) ||
          (httpResponseCode == HTTP_CODE_PERMANENT_REDIRECT)) {
        url = (char *)http.header("Location").c_str();
        if (url == NULL) {
          url = dev->url0;
        }
        Serial.print("HTTP Location header: ");
        Serial.println(url);
        set_data_url(dev, url);
        httpResponseCode = 0;
        j++;
      }

      // Free resources
      http.end();
      client->stop();
      delay(500);

      // done ...
      if (httpResponseCode == HTTP_CODE_OK) {
        break;
      }

      // retry after error; reset url
      if (httpResponseCode < 0) {
        url = dev->url0;
        set_data_url(dev, url);
        k++;
      }

      // // restart in case heap memory is low
      // if (httpResponseCode == HTTPC_ERROR_CONNECTION_REFUSED) {
      //   Serial.println("oom: rebooting ...");
      //   ESP.restart();
      //   break;
      // }

      // failed on sending request
      if ((j == MAX_REDIR) || (k == MAX_ATTEMPTS)) {
        Serial.print("sending request failed on: ");
        Serial.println(dev->url);
        break;
      }
    }
  }
}

/// @brief battery characteristic callback function
/// @return
static void
notifyBatteryCallback(BLERemoteCharacteristic *pBLERemoteCharacteristic,
                      BLEAddress BLEAddr, uint8_t *pData, size_t length,
                      bool isNotify) {
  //BLEDevice::getScan()->stop();
  long double ts = (long double)get_epoch_time();
  int i = index_by_mac(BLEAddr.toString().c_str());
  int j = next_index(&myDev[i], ts);
  int val = (int)(*pData);
  s_data *dat = &(myDev[i].data[j]);
  set_data_bat(dat, val);
  Serial.printf("[%.9e]: BAT  CB / MAC: %s / DEV: %d/%d / VAL: %d\n", ts,
                myDev[i].mac, i, j, val);
}

/// @brief temperature characteristic callback function
/// @return
static void
notifyTemperatureCallback(BLERemoteCharacteristic *pBLERemoteCharacteristic,
                          BLEAddress BLEAddr, uint8_t *pData, size_t length,
                          bool isNotify) {
  //BLEDevice::getScan()->stop();
  long double ts = (long double)get_epoch_time();
  int i = index_by_mac(BLEAddr.toString().c_str());
  int j = next_index(&myDev[i], ts);
  int val = (int)(*pData);
  s_data *dat = &(myDev[i].data[j]);
  set_data_temp(dat, val);
  Serial.printf("[%.9e]: TEMP CB / MAC: %s / DEV: %d/%d / VAL: %d\n", ts,
                myDev[i].mac, i, j, val);
}
/// @brief movement characteristic callback function
/// @return
static void
notifyMovementCallback(BLERemoteCharacteristic *pBLERemoteCharacteristic,
                       BLEAddress BLEAddr, uint8_t *pData, size_t length,
                       bool isNotify) {
  //BLEDevice::getScan()->stop();
  long double ts = (long double)get_epoch_time();
  int i = index_by_mac(BLEAddr.toString().c_str());
  int j = next_index(&myDev[i], ts);
  int val = (int)(*pData);
  s_data *dat = &(myDev[i].data[j]);
  set_data_mov(dat, val);
  Serial.printf("[%.9e]: MOV  CB / MAC: %s / DEV: %d/%d / VAL: %d\n", ts,
                myDev[i].mac, i, j, val);
}

/// @brief button characteristic callback function
/// @return
static void
notifyButtonCallback(BLERemoteCharacteristic *pBLERemoteCharacteristic,
                     BLEAddress BLEAddr, uint8_t *pData, size_t length,
                     bool isNotify) {
  //BLEDevice::getScan()->stop();
  long double ts = (long double)get_epoch_time();
  int i = index_by_mac(BLEAddr.toString().c_str());
  int j = next_index(&myDev[i], ts);
  int val = (int)(*pData);
  s_data *dat = &(myDev[i].data[j]);
  set_data_btn(dat, val);
  Serial.printf("[%.9e]: BTN  CB / MAC: %s / DEV: %d/%d / VAL: %d\n", ts,
                myDev[i].mac, i, j, val);
}

/// @brief BLE client callback class
/// @return
class MyClientCallback : public BLEClientCallbacks {
  void onConnect(BLEClient *pclient) {
    int i = index_by_client(pclient);
    if (i == NO_INDEX) {
      Serial.println("ERROR: wrong onConnect() index");
    }
  }

  void onDisconnect(BLEClient *pclient) {
    int i = index_by_client(pclient);
    if (i == NO_INDEX) {
      Serial.println("ERROR: wrong onDisconnect() index");
    } else {
      if (myDev[i].state != D_DISCONNECTED) {
        reset_device(i);
      }
      Serial.print("onDisconnect... ");
      Serial.printf("MAC [%s] [%d]\n", myDev[i].mac, i);
    }
  }
};

/// @brief starts connection to a BLE server
/// @return
void connectToServer() {
  int i = NO_INDEX;

  while (1) {
    // connect only if this server was scanned and is known
    i = index_by_state(D_SCANNED);
    if (i == NO_INDEX) {
      break;
    }
    // set device state to connected
    myDev[i].state = D_CONNECTED;

    Serial.print("connecting to ");
    Serial.println(myDev[i].mac);

    myDev[i].pClient = BLEDevice::createClient();
    Serial.println(" - created client");

    myDev[i].pClient->setClientCallbacks(new MyClientCallback());

    // connect to the remote BLE server
    myDev[i].pClient->connect(myDev[i].pDevice);
    Serial.println(" - connected to server");

    // obtain a reference to the service we are after in the remote BLE server
    BLERemoteService *pRemoteService =
        myDev[i].pClient->getService(serviceUUID);
    if (pRemoteService == nullptr) {
      Serial.print("failed to find our service UUID: ");
      Serial.println(serviceUUID.toString().c_str());
      myDev[i].pClient->disconnect();
      reset_device(i);
      continue;
    }
    Serial.println(" - found service");

    // Read the value of the characteristic.
    BLERemoteCharacteristic *pRemoteCharacteristic =
        pRemoteService->getCharacteristic(serviceUUID);
    if (pRemoteCharacteristic->canRead()) {
      std::string value = pRemoteCharacteristic->readValue();
      Serial.print(" - characteristic value is: ");
      Serial.println(value.c_str());
      set_characteristic(&myDev[i], value.c_str());
      Serial.printf(" --  id: %s\n", myDev[i].id);
      Serial.printf(" -- url: %s\n", myDev[i].url);
    }

    // obtain references to the characteristics in the service ...
    // battery characteristic characteristic
    myDev[i].batCharacteristic =
        pRemoteService->getCharacteristic(batCharacteristicUUID);
    if (myDev[i].batCharacteristic == nullptr) {
      Serial.print("Failed to find our characteristic UUID: ");
      Serial.println(batCharacteristicUUID.toString().c_str());
      myDev[i].pClient->disconnect();
      reset_device(i);
      continue;
    }
    Serial.println(" - found battery characteristic");

    // register battery characteristic callback
    if (myDev[i].batCharacteristic->canNotify()) {
      myDev[i].batCharacteristic->registerForNotify(notifyBatteryCallback);
    }

    // temperature characteristic
    myDev[i].tempCharacteristic =
        pRemoteService->getCharacteristic(tempCharacteristicUUID);
    if (myDev[i].tempCharacteristic == nullptr) {
      Serial.print("Failed to find our characteristic UUID: ");
      Serial.println(tempCharacteristicUUID.toString().c_str());
      myDev[i].pClient->disconnect();
      reset_device(i);
      continue;
    }
    Serial.println(" - found temperature characteristic");

    // register temperature characteristic callback
    if (myDev[i].tempCharacteristic->canNotify()) {
      myDev[i].tempCharacteristic->registerForNotify(notifyTemperatureCallback);
    }

    // movement characteristic
    myDev[i].movCharacteristic =
        pRemoteService->getCharacteristic(movCharacteristicUUID);
    if (myDev[i].movCharacteristic == nullptr) {
      Serial.print("Failed to find our characteristic UUID: ");
      Serial.println(movCharacteristicUUID.toString().c_str());
      myDev[i].pClient->disconnect();
      reset_device(i);
      continue;
    }
    Serial.println(" - found movement characteristic");

    // register movement characteristic callback
    if (myDev[i].movCharacteristic->canNotify()) {
      myDev[i].movCharacteristic->registerForNotify(notifyMovementCallback);
    }

    // button characteristic
    myDev[i].btnCharacteristic =
        pRemoteService->getCharacteristic(btnCharacteristicUUID);
    if (myDev[i].btnCharacteristic == nullptr) {
      Serial.print("Failed to find our characteristic UUID: ");
      Serial.println(btnCharacteristicUUID.toString().c_str());
      myDev[i].pClient->disconnect();
      reset_device(i);
      continue;
    }
    Serial.println(" - found button characteristic");

    // register button characteristic callback
    if (myDev[i].btnCharacteristic->canNotify()) {
      myDev[i].btnCharacteristic->registerForNotify(notifyButtonCallback);
    }
  }

  Serial.println("done.");

  return;
}

/// @brief scan for BLE servers with known MAC and service
/// @return
class MyAdvertisedDeviceCallbacks : public BLEAdvertisedDeviceCallbacks {
  // called for each advertising BLE server
  void onResult(BLEAdvertisedDevice advertisedDevice) {
    Serial.print("BLE Advertised Device found: ");
    Serial.println(advertisedDevice.toString().c_str());
    // device found, lets now see if it contains proper MAC and service
    if (advertisedDevice.haveServiceUUID() &&
        advertisedDevice.isAdvertisingService(serviceUUID) &&
        has_mac(advertisedDevice.getAddress().toString().c_str())) {
      BLEDevice::getScan()->stop();
      // get the next unused device index
      if (index_by_mac(advertisedDevice.getAddress().toString().c_str()) ==
          NO_INDEX) {
        int i = index_by_state(D_DISCONNECTED);
        if (i == NO_INDEX) {
          Serial.println("ERROR: no free index");
        } else {
          myDev[i].state = D_SCANNED;
          myDev[i].pDevice = new BLEAdvertisedDevice(advertisedDevice);
          strcpy(myDev[i].mac,
                 advertisedDevice.getAddress().toString().c_str());
        }
      }
    }
  }
};

/// @brief ESP 32 device setup
/// @return
void setup() {
  delay(1000);
  Serial.begin(115200);

  // Watchdog Configuration
  // Serial.println("configuring WatchDogTimer ...");

  // esp_task_wdt_init(WDT_TIMEOUT, true);
  // esp_task_wdt_add(NULL);

  Serial.println();

  if (pref.begin("dec4iot", false)) {
    Serial.println("EEPROM storage initialized");
  }

  Config.autoReset = false;     // Not reset the module even by intentional disconnection using AutoConnect menu.
  Config.autoReconnect = true;  // Reconnect to known access points.
  Config.reconnectInterval = 6; // Reconnection attempting interval is 3[min].
  Config.retainPortal = true;   // Keep the captive portal open.
  Config.homeUri="/_ac";
  Config.title = "DEC4IOT";
  Portal.config(Config);
  Portal.load(FPSTR(AUX_DEC4IOT));

  Portal.on("/dec4iot", loadOn, AC_EXIT_AHEAD);
  Portal.on("/save", saveOn, AC_EXIT_AHEAD);

  if (Portal.begin()) {
    Serial.println("WiFi connected: " + WiFi.localIP().toString());
    configTime(TZ[TZindex].tzoff * GMT_OFF_SEC, DLT_OFF_SEC, TZ[TZindex].ntpServer);
  }

  Serial.printf("TIME [%.9e] HEAP [%lu] ", (long double)get_epoch_time(),
                (unsigned long)ESP.getFreeHeap());
  Serial.println("starting Arduino BLE Client application...");

  client = new WiFiClientSecure;
  client->setInsecure();

  location = get_location();

  delay(4000);
  reset_devices();

  Serial.printf("TIME [%.9e] HEAP [%lu]\n", (long double)get_epoch_time(),
                (unsigned long)ESP.getFreeHeap());

  BLEDevice::init(DEV_NAME);

  pBLEScan = BLEDevice::getScan();
  pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
  pBLEScan->setInterval(1349);
  pBLEScan->setWindow(449);

  if (getMACs() == 1) {
    isConfigured = true;
    pBLEScan->setActiveScan(true);
    Serial.println("BLE MACs found!");
  } else {
    pBLEScan->setActiveScan(false);
    Serial.println("BLE MACs not found!");
  }
}

/// @brief ESP 32 main loop
/// @return
void loop() {
  int i;

  Portal.handleClient();

  if (isConfigured) {
    // connect to BLE server
    for (i = 0; i < MAX_DEVICE; i++) {
      if (myDev[i].state == D_SCANNED) {
        Serial.printf("connecting ... [%d]\n", i);
        connectToServer();
      }
    }
    // check for JSON data to send
    for (i = 0; i < MAX_DEVICE; i++) {
      if (myDev[i].state == D_CONNECTED) {
        Serial.printf("checking data ... [%d]\n", i);
        for (int j = 0; j < MAX_POOL; j++) {
          s_data *d = &(myDev[i].data[j]);
          if (check_data(d)) {
            Serial.printf("TIME [%.9e] HEAP [%lu]\n", d->tm,
                          (unsigned long)ESP.getFreeHeap());
            send_json(&myDev[i], d);
            reset_data(d);
          }
        }
      }
    }
    // start scan if we can connect a new device
    // (after disconnect or if no device is connected)
    int j = index_by_state(D_DISCONNECTED);
    if (j == NO_INDEX) {
      Serial.println("ERROR: no free index");
    } else {
      Serial.printf("scanning ... [%d]\n", j);
      BLEDevice::getScan()->start(2);
    }
  }

  // esp_task_wdt_reset();
}
