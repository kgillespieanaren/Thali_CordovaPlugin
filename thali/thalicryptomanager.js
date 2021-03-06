'use strict';

var crypto = require('crypto');
var fs = require('fs');
var path = require('path');

// The password is not secure because anyone who can get to the file can get
// to the app and thus can get the password. The password is used here only to
// satisfy the crypto/PKCS12 APIs.
var password = 'password';
var certname = 'certname';
var country = 'country';
var organization = 'organization';
var pkcs12FileName = '/pkcs12.pfx';
var macName = 'SHA256';
var hashSizeInBytes = 16;

/**
* Checks if a PKCS12 file exists in a known location and if not present, it is
* created. The public key is extracted from the PKCS12 content and it's SHA256
* hash value is returned.
* @param {Function} cb the callback which returns an error or the hash value.
*/
exports.getPublicKeyHash = function (cb) {
  Mobile.GetDocumentsPath(function (err, fileLocation) {
    if (err) {
      cb(err);
      return;
    }

    var file = path.join(fileLocation, pkcs12FileName);
    getPKCS12Content(file, function(err, pkcs12Content) {
      if (err) {
        cb(err);
        return;
      }

      try {
        var publicKey = crypto.pkcs12
          .extractPublicKey(password, pkcs12Content);
        if (!publicKey || publicKey.length <= 0) {
          cb('extracted public key is invalid');
          return;
        }
        var hash = exports.generateSlicedSHA256Hash(publicKey, hashSizeInBytes);
        cb(null, hash);
      } catch(e) {
        cb('error thrown by extractPublicKey() function');
      }
    });
  });
};

exports.generateSlicedSHA256Hash = function(bufferValueToHash, hashSizeInBytes) {
  var hash = crypto.createHash(macName);
  hash.update(bufferValueToHash);
  var fullSizeKeyHashBuffer = hash.digest();
  var slicedBuffer = fullSizeKeyHashBuffer.slice(0, hashSizeInBytes);
  return slicedBuffer.toString('base64');
};

exports.getConfigValuesForTestingOnly = function() {
  return {
    password: password,
    certname: certname,
    country: country,
    organization: organization,
    pkcs12FileName: pkcs12FileName,
    hashSizeInBytes: hashSizeInBytes
  };
};

/**
* Reads the PKCS12 content from the given file if it exists. If not,
* the PKCS12 content is generated, saved to a file and the content is
* returned.
* @param {String} fileNameWithPath the file which has the PKCS12 content.
* @param {Function} cb the callback which returns an error or PKCS12 content.
*/
function getPKCS12Content(fileNameWithPath, cb) {
  fs.exists(fileNameWithPath, function (exists) {
    if(exists) {
      fs.readFile(fileNameWithPath, function (err, pkcs12Content) {
        if (err) {
          cb(err);
          return;
        }
        cb(null, pkcs12Content);
      });
    } else {
      var pkcs12Content = crypto.pkcs12
        .createBundle(password, certname, country, organization);
      if (pkcs12Content.length <= 0) {
        cb('failed to create pkcs12Content');
        return;
      }
      fs.writeFile(fileNameWithPath, pkcs12Content, {flags: 'wx'}, function (err) {
        if (err) {
          cb(err);
          return;
        }
        cb(null, pkcs12Content);
      });
    }
  });
}
