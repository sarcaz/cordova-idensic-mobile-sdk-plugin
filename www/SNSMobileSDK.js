// eslint-disable-next-line import/no-unresolved
const exec = require('cordova/exec');

const PLUGIN_NAME = 'SNSMobileSdkCordovaPlugin';

const LAUNCH_SUMSUB_SDK_ACTION = 'launchSNSMobileSDK';
const SET_NEW_TOKEN_SUMSUB_SDK_ACTION = 'setNewAccessToken';
const DISMISS_ACTION = 'dismiss';

const SUMSUB_SDK_HANDLERS = {
    'onStatusChanged': null,
    'onEvent': null,
    'onActionResult': 'onActionResultCompleted'
}

/*
SNSMobileSDK.Builder(apiUrl)
  .withAccessToken($token, () => { return new Promise((resolve, reject) => { resolve("..."); })
  .withModules([..., ...])
  .withDebug(true)
  .withHandlers({
    onCompleted: () => {},
    onError: () => {}
  }).launch()
 */

var _currentInstance = null;

function SNSMobileSDK(sdkConf) {
    this.sdkConf = sdkConf

    this.sdkConf.settings["appFrameworkName"] = "cordova";
    // this.sdkConf.settings["appFrameworkVersion"] = "...";
}

SNSMobileSDK.prototype.dismiss = function () {
    exec(
        (result) => {
        },
        (error) => {
        },
        PLUGIN_NAME,
        DISMISS_ACTION,
        []
    );
}

SNSMobileSDK.prototype.sendEvent = function (name, event) {
    var handler = this.sdkConf.handlers[name];

    if (!handler) {
        return;
    }

    var completionAction = SUMSUB_SDK_HANDLERS[name];

    if (!completionAction) {
        handler(event);
        return;
    }

    var onComplete = function (error, result) {
        exec(
            (result) => {
            },
            (error) => {
            },
            PLUGIN_NAME,
            completionAction,
            [{
                "error": error, 
                "result": result
            }]
        );
    }

    handler(event)
        .then(result => {
            onComplete(null, result)
        })
        .catch(error => {
            onComplete(error || new Error("rejected"), null)
        })
}

SNSMobileSDK.prototype.getNewAccessToken = function () {
    var onComplete = function (newToken) {
        exec(
            (result) => {
            },
            (error) => {
            },
            PLUGIN_NAME,
            SET_NEW_TOKEN_SUMSUB_SDK_ACTION,
            [newToken]
        );
    }

    this.sdkConf.tokenExpirationHandler()
        .then(newToken => {
            onComplete(newToken)
        })
        .catch(err => {
            console.error(err instanceof Error ? err.message : err)
            onComplete(null)
        })
}

SNSMobileSDK.prototype.launch = function () {
    let _that = this;
    console.log("InSumSub", JSON.stringify(this.sdkConf));
    return new Promise((resolve, reject) => {
        if (_currentInstance) {
            reject(new Error("Aborted since another instance is in use!"));
        }
        else if (!_that.sdkConf.accessToken) {
            reject(new Error('Access token is required'));
        }
        else {
            _currentInstance = _that
            exec(
                (result) => {
                    _currentInstance = null
                    console.log("Promise SumSub Result", JSON.stringify(result));
                    resolve(result);
                },
                (error) => {
                    _currentInstance = null
                    console.log("Promise SumSub Error", JSON.stringify(error));
                    reject(error);
                },
                PLUGIN_NAME,
                LAUNCH_SUMSUB_SDK_ACTION,
                [_that.sdkConf],
            );
        }
    });
}

function Builder() {
    this.debug = false;
    this.handlers = {};
    this.applicantConf = {};
    this.preferredDocumentDefinitions = {};
    this.autoCloseOnApprove = 3;
    this.settings = {};
    this.disableMLKit = false
    return this;
}

Builder.prototype.withAccessToken = function (accessToken, expirationHandler) {
    this.accessToken = accessToken
    if (!expirationHandler || typeof expirationHandler !== 'function') {
        throw new Error('Invalid parameter, "expirationHandler" must be a function')
    }
    this.tokenExpirationHandler = expirationHandler
    return this
}

Builder.prototype.withHandlers = function (handlers) {

    if (!handlers || typeof handlers !== 'object') {
        throw new Error('Invalid parameter, "withHandlers" expects a hash')
    }

    Object.keys(SUMSUB_SDK_HANDLERS).forEach(name => {
        var handler = handlers[name];
        if (handler) {
            if (typeof handler !== 'function') {
                throw new Error('Invalid handler, "'+name+'" must be a function')
            }
            this.handlers[name] = handler;
        }
    })

    return this
}

Builder.prototype.withDebug = function (flag) {
    if (typeof flag !== 'boolean') {
        throw new Error('Invalid parameter, "withDebug" expects a boolean');
    }
    this.debug = flag;
    return this;
}

Builder.prototype.withAnalyticsEnabled = function (flag) {
    if (typeof flag !== 'boolean') {
        throw new Error('Invalid parameter, "withAnalyticsEnabled" expects a boolean');
    }
    this.isAnalyticsEnabled = flag;
    return this;
}

Builder.prototype.withLocale = function (locale) {
    if (typeof locale !== 'string') {
        throw new Error('Invalid parameter, "locale" must be a string');
    }
    this.locale = locale;
    return this;
}

Builder.prototype.withApplicantConf = function (applicantConf) {
    if (!applicantConf || typeof applicantConf !== 'object') {
        throw new Error('Invalid parameter, "withApplicantConf" expects a hash');
    }
    this.applicantConf = applicantConf;
    return this
}

Builder.prototype.withPreferredDocumentDefinitions = function (preferredDocumentDefinitions) {
    if (!preferredDocumentDefinitions || typeof preferredDocumentDefinitions !== 'object') {
        throw new Error('Invalid parameter, "withPreferredDocumentDefinitions" expects a hash');
    }
    this.preferredDocumentDefinitions = preferredDocumentDefinitions;
    return this
}

Builder.prototype.withSettings = function (settings) {
    if (!settings || typeof settings !== 'object') {
        throw new Error('Invalid parameter, "withSettings" expects a hash');
    }
    this.settings = settings;
    return this
}

Builder.prototype.withStrings = function (strings) {

    if (!strings || typeof strings !== 'object') {
        throw new Error('Invalid parameter, "withStrings" expects a hash')
    }
    this.strings = strings;
    return this
}

Builder.prototype.withTheme = function (theme) {
    if (!theme || typeof theme !== 'object') {
        throw new Error('Invalid parameter, "withTheme" expects a hash')
    }
    this.theme = theme;
    return this
}

Builder.prototype.withBaseUrl = function (apiUrl) {
    if (typeof apiUrl !== 'string') {
        throw new Error('Invalid parameter, "baseUrl" must be a string');
    }
    this.apiUrl = apiUrl;
    return this;
}

Builder.prototype.withAutoCloseOnApprove = function (autoCloseOnApprove) {
    if (typeof autoCloseOnApprove !== 'number') {
        throw new Error('Invalid parameter, "autoCloseOnApprove" expects a number')
    }
    this.autoCloseOnApprove = autoCloseOnApprove;
    return this
}

Builder.prototype.withDisableMLKit = function (flag) {
    if (typeof flag !== 'boolean') {
        throw new Error('Invalid parameter, "withDisableMLKit" expects a boolean');
    }
    this.disableMLKit = flag;
    return this;
}

Builder.prototype.build = function () {

    var hasHandlers = {}
    Object.keys(this.handlers).forEach(name => {
        hasHandlers[name] = true;
    })

    return new SNSMobileSDK({
        apiUrl: this.apiUrl,
        accessToken: this.accessToken,
        tokenExpirationHandler: this.tokenExpirationHandler,
        handlers: this.handlers,
        hasHandlers: hasHandlers,
        locale: this.locale,
        applicantConf: this.applicantConf,
        preferredDocumentDefinitions: this.preferredDocumentDefinitions,
        settings: this.settings,
        theme: this.theme,
        strings: this.strings,
        isAnalyticsEnabled: this.isAnalyticsEnabled,
        autoCloseOnApprove: this.autoCloseOnApprove,
        debug: this.debug,
        disableMLKit: this.disableMLKit
    });
}


module.exports = {
    init: function (accessToken, expirationHandler) {
        return new Builder().withAccessToken(accessToken, expirationHandler)
    },
    getNewAccessToken: function () {
        if (_currentInstance) {
            _currentInstance.getNewAccessToken()
        }
    },
    sendEvent: function (name, event) {
        if (_currentInstance) {
            _currentInstance.sendEvent(name, event)
        }
    },
    reset: function() {
        _currentInstance = null
    }
};
