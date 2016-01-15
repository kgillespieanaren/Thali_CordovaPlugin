'use strict';

var ThaliWifiInfrastructure = require('thali/ThaliWifiInfrastructure');
var tape = require('../lib/thali-tape');
var nodessdp = require('node-ssdp');

var TEST_DEVICE_NAME = 'testDeviceName'

var wifiInfrastructure = new ThaliWifiInfrastructure(TEST_DEVICE_NAME);

var test = tape({
  setup: function(t) {
    t.end();
  },
  teardown: function(t) {
    // Stop everything at the end of tests to make sure the next test starts from clean state
    wifiInfrastructure.stopAdvertisingAndListeningForIncomingConnections()
    .then(function () {
      return wifiInfrastructure.stopListeningForAdvertisements();
    })
    .then(function () {
      t.end();
    });
  }
});

test('#startListeningForAdvertisements should emit wifiPeerAvailabilityChanged after test peer becomes available', function (t) {
  var testLocation = 'http://foo.bar/baz';
  var testServer = new nodessdp.Server({
    location: testLocation,
    allowWildcards: true,
    adInterval: 500,
    udn: 'somePeerDeviceName' + ':' + wifiInfrastructure.thaliUsn
  });
  wifiInfrastructure.on('wifiPeerAvailabilityChanged', function (data) {
    t.equal(data[0].peerLocation, testLocation);
    testServer.stop(function () {
      t.end();
    });
  });
  testServer.start(function () {
    wifiInfrastructure.startListeningForAdvertisements();
  });
});

test('#startUpdateAdvertisingAndListenForIncomingConnections should use different USN after every invocation', function (t) {
  var originalAddUsn = wifiInfrastructure._server.addUSN;
  var currentUsn;
  wifiInfrastructure._server.addUSN = function(usn) {
    currentUsn = usn;
  };
  wifiInfrastructure.startUpdateAdvertisingAndListenForIncomingConnections()
  .then(function() {
    var firstUsn = currentUsn;
    wifiInfrastructure.startUpdateAdvertisingAndListenForIncomingConnections()
    .then(function() {
      t.notEqual(firstUsn, currentUsn);
      wifiInfrastructure._server.addUSN = originalAddUsn;
      t.end();
    });
  });
});

test('verify that Thali-specific messages are filtered correctly', function (t) {
  var irrelevantMessage = {
    USN: 'foobar'
  };
  t.equal(true, wifiInfrastructure.shouldBeIgnored(irrelevantMessage), 'irrelevant messages should be ignored');
  var relevantMessage = {
    USN: wifiInfrastructure.thaliUsn
  };
  t.equal(false, wifiInfrastructure.shouldBeIgnored(relevantMessage), 'relevant messages should not be ignored');
  var messageFromSelf = {
    USN: TEST_DEVICE_NAME + ':' + wifiInfrastructure.thaliUsn
  };
  t.equal(true, wifiInfrastructure.shouldBeIgnored(messageFromSelf), 'messages from this device should be ignored');
  t.end();
});
