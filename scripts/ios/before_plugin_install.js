'use strict';

var fs = require('fs');

module.exports = function (context) {

    var withMRTDReader = process.argv.includes('--with-MRTDReader')
    var withMRTDReaderCompat = process.argv.includes('--with-MRTDReader-compat')
    var withVideoIdent = process.argv.includes('--with-VideoIdent')
    var withVideoIdentLatest = process.argv.includes('--with-VideoIdent-latest')
    
    withMRTDReader = withMRTDReader || withMRTDReaderCompat
    withVideoIdent = withVideoIdent || withVideoIdentLatest 

    if (!withMRTDReader && !withVideoIdent) {
        return
    }

    var podfilePath = './platforms/ios/Podfile'

    fs.stat(podfilePath, function (error, stat) {
        if (error) {
            return
        }

        var podfileContent = fs.readFileSync(podfilePath, 'utf8')
        var podLineRe = /^.*IdensicMobileSDK.*$/m

        var matches = podfileContent.match(podLineRe)
        if (!matches) {
            return
        }

        var podLine = matches[0]
        var replaces = [podLine]

        if (withMRTDReader) {
            replaces.push(podLine.replace(/IdensicMobileSDK/, 'IdensicMobileSDK/MRTDReader' + (withMRTDReaderCompat ? "-compat" : "")))
        }
        if (withVideoIdent) {
            replaces.push(podLine.replace(/IdensicMobileSDK/, 'IdensicMobileSDK/VideoIdent' + (withVideoIdentLatest ? "-latest" : "")))
        }

        podfileContent = podfileContent.replace(podLineRe, replaces.join("\n"))

        fs.writeFileSync(podfilePath, podfileContent, 'utf8')
    })
} 