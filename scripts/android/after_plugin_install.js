'use strict';

var fs = require('fs');

module.exports = function (context) {

    var withVideoIdent = process.argv.includes('--with-VideoIdent')

    if (!withVideoIdent) {
        return
    }

    var gradleExtrasFilePath = './platforms/android/cordova-idensic-mobile-sdk-plugin/SumSubCordova-build-extras.gradle'

    fs.readFile(gradleExtrasFilePath, 'utf8', function (err,data) {

     var formatted = data.replace(/^\/\/(\s+implementation\s.+videoident.+)$/m, "$1");

     fs.writeFile(gradleExtrasFilePath, formatted, 'utf8', function (err) {
        if (err) return console.log(err);
     });
    });

}