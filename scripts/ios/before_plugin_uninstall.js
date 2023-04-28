'use strict';

var fs = require('fs');

module.exports = function (context) {

    var podfilePath = './platforms/ios/Podfile'

    fs.stat(podfilePath, function (error, stat) {
        if (error) {
            return
        }

        var podfileContent = fs.readFileSync(podfilePath, 'utf8')

        podfileContent = podfileContent.replace(/^.*IdensicMobileSDK\/\w+.*$/gm, '')

        fs.writeFileSync(podfilePath, podfileContent, 'utf8')
    })
} 