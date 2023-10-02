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
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
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
 *  @date    03-2023
 *  @version 1.0
 *
 *  @brief ESP32 BLE-SENML Gateway (up to 4 BLEServer)
 */

/******************************************************************* INCLUDE */

#include <Arduino.h>
#include <BLEDevice.h>
#include <HTTPClient.h>
#include <esp_task_wdt.h>
#include <WiFi.h>

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
#define NO_INDEX -1

#define WDT_TIMEOUT 600

// adjust this part if necessary
#define DEV_NAME "DEC112-BLE-Client"
#define MAC_1 "68:72:c3:eb:8e:a9"
#define MAC_2 "d6:ea:13:f5:11:3b"
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

/******************************************************************* GLOBALS */

// adjust this part if necessary
const char *ssid = "xxxxxx";
const char *password = "xxxxxx";
const char *ntpServer = "pool.ntp.org";

const char* mozillaApi = "https://location.services.mozilla.com/v1/geolocate?key=test";

const long gmtOffset_sec = GMT_OFF_SEC;
const int daylightOffset_sec = DLT_OFF_SEC;

char myMacs[MAX_DEVICE][MAC_SIZE] = {MAC_1, MAC_2, MAC_3, MAC_4};


static location_t location;
static s_device myDev[MAX_DEVICE];
static boolean doScan = false;
static WiFiClientSecure *client;
static HTTPClient http;

// BLE Service
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

/// @brief  converts MAC address to string
/// @return string
String mac_to_string(uint8_t* macAddress) {
  char macStr[18] = { 0 };
  sprintf(macStr, "%02X:%02X:%02X:%02X:%02X:%02X", macAddress[0], macAddress[1], macAddress[2], macAddress[3], macAddress[4], macAddress[5]);

  return  String(macStr);
}

/// @brief  looks for surrounding SSIDs
/// @return JSON including WiFi information
String get_surrounding_wifi_json() {
  String wifiArray = "[\n";
  int8_t numWifi = WiFi.scanNetworks();

  for (uint8_t i = 0; i < numWifi; i++) {//numWifi; i++) {
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
          tempStr = tempStr.substring(tempStr.indexOf(":") + 1, tempStr.indexOf(","));
          snprintf(location.lat, LOC_SIZE, "%s", tempStr);
          Serial.printf("Lat: %s\n", location.lat);
        }

        index = response.indexOf("\"lng\":");
        if (index != -1) {
          String tempStr = response.substring(index);
          tempStr = tempStr.substring(tempStr.indexOf(":") + 1, tempStr.indexOf("}"));
          snprintf(location.lon, LOC_SIZE, "%s", tempStr);
          Serial.printf("Lon: %s\n", location.lon);
        }

        index = response.indexOf("\"accuracy\":");
        if (index != -1) {
          String tempStr = response.substring(index);
          tempStr = tempStr.substring(tempStr.indexOf(":") + 1, tempStr.indexOf("\n"));
          location.accuracy = tempStr.toFloat();
          Serial.println("Accuracy: " + String(location.accuracy) + "\n");
        }
      }
    } else {
      Serial.printf("[HTTPS] POST... failed, error: %s\n", http.errorToString(httpResponseCode).c_str());
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

  while(1) {
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

  snprintf(d->url, len + strlen(URL_PREFIX), URL_PREFIX"%s", tmp);

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

/// @brief  initialize WiFi and synchronize local time
/// @return
void start_wifi() {
  int count = 0;
  delay(100);

  WiFi.mode(WIFI_STA);
  WiFi.disconnect();
  delay(100);
  WiFi.begin(ssid, password);
  Serial.print("connecting Wifi ");
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
    count++;
    if (count == 20) {
      break;
    }
  }
  if (count < 20) {
    Serial.println("");
    Serial.print("connected to WiFi network with IP Address: ");
    Serial.println(WiFi.localIP());
    configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
  } else {
    Serial.println("");
    Serial.println("WiFi not connected");
  }
  return;
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
        jsonb_key(&b, buf, ELEMENT_SIZE, "u", strlen("u"));
        jsonb_string(&b, buf, ELEMENT_SIZE, "lat", strlen("lat"));
        jsonb_key(&b, buf, ELEMENT_SIZE, "v", strlen("v"));
        jsonb_number(&b, buf, ELEMENT_SIZE, strtod(location.lat, NULL));
        jsonb_object_pop(&b, buf, ELEMENT_SIZE);
      }
      {
        jsonb_object(&b, buf, ELEMENT_SIZE);
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

  // reconnect, if WiFi was lost
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("WiFi lost, trying to reconnect ...");
    start_wifi();
  }

  if (WiFi.status() == WL_CONNECTED) {
    char *url = dev->url;
    const char *headerKeys[] = {"Location"};
    const size_t headerKeysCount = sizeof(headerKeys) / sizeof(headerKeys[0]);
    String httpRequestData = buf;
    int httpResponseCode = 0;
    int j = 0;

    client->setInsecure();
    while(1) {
      http.collectHeaders(headerKeys, headerKeysCount);
      http.begin(*client, url);

      http.addHeader("Content-Type", "application/json");
      http.addHeader("User-Agent", "ESP32");
   
      httpResponseCode = http.POST(httpRequestData);
      Serial.print("HTTP Response code: ");
      Serial.println(httpResponseCode);

      if (httpResponseCode == HTTP_CODE_MOVED_PERMANENTLY) {
        url = (char*)http.header("Location").c_str();
        Serial.print("HTTP Location header: ");
        Serial.println(url);
        httpResponseCode = 0;
      }
      // store url for next request
      if (httpResponseCode == HTTP_CODE_OK) {
        set_data_url(dev, url);
      }
      // Free resources
      http.end();
      client->stop();
      j++;
      delay(500);

      // restart in case heap memory is low
      if (httpResponseCode == -1) {
        ESP.restart();
        break;
      }

      if ((httpResponseCode == HTTP_CODE_OK) || (j == MAX_REDIR)) {
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
  BLEDevice::getScan()->stop();
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
  BLEDevice::getScan()->stop();
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
  BLEDevice::getScan()->stop();
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
  BLEDevice::getScan()->stop();
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
    BLERemoteCharacteristic *pRemoteCharacteristic = pRemoteService->getCharacteristic(serviceUUID);
    if(pRemoteCharacteristic->canRead()) {
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
  Serial.begin(115200);

  // Watchdog Konfiguration
  //Serial.println("configuring WatchDogTimer ...");

  //esp_task_wdt_init(WDT_TIMEOUT, true);        
  //esp_task_wdt_add(NULL);

  Serial.printf("TIME [%.9e] HEAP [%lu] ", (long double)get_epoch_time(),
                (unsigned long)ESP.getFreeHeap());
  Serial.println("starting Arduino BLE Client application...");

  start_wifi();

  client = new WiFiClientSecure;
  client->setInsecure();

  location = get_location();

  delay(4000);
  reset_devices();

  Serial.printf("TIME [%.9e] HEAP [%lu]\n", (long double)get_epoch_time(),
                (unsigned long)ESP.getFreeHeap());

  BLEDevice::init(DEV_NAME);

  BLEScan *pBLEScan = BLEDevice::getScan();
  pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
  pBLEScan->setInterval(1349);
  pBLEScan->setWindow(449);
  pBLEScan->setActiveScan(true);
  pBLEScan->start(5, false);
}

/// @brief ESP 32 main loop
/// @return
void loop() {
  int i;
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
  // start scan after disconnect or if not connected
  for (i = 0; i < MAX_DEVICE; i++) {
    if (myDev[i].state == D_DISCONNECTED) {
      Serial.printf("scanning ... [%d]\n", i);
      BLEDevice::getScan()->start(0);
    }
  }

  // delay between loops
  delay(1000);
  //esp_task_wdt_reset();
}
