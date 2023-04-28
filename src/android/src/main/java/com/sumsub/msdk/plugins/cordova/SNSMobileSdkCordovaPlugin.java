package com.sumsub.msdk.plugins.cordova;

import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.ValueCallback;

import androidx.annotation.Nullable;

import com.sumsub.sns.R;
import com.sumsub.sns.core.*;
import com.sumsub.sns.core.data.listener.SNSActionResultHandler;
import com.sumsub.sns.core.data.listener.SNSCompleteHandler;
import com.sumsub.sns.core.data.listener.SNSErrorHandler;
import com.sumsub.sns.core.data.listener.SNSEventHandler;
import com.sumsub.sns.core.data.listener.SNSIconHandler;
import com.sumsub.sns.core.data.listener.SNSStateChangedHandler;
import com.sumsub.sns.core.data.model.*;
import com.sumsub.sns.core.theme.SNSCustomizationFileFormat;
import com.sumsub.sns.prooface.SNSProoface;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class SNSMobileSdkCordovaPlugin extends CordovaPlugin {
    private static final String LAUNCH_ACTION = "launchSNSMobileSDK";
    private static final String NEW_TOKEN_ACTION = "setNewAccessToken";
    private static final String ACTION_COMPLETED_ACTION = "onActionResultCompleted";
    private static final String DISMISS_ACTION = "dismiss";

    private static final String TAG = "SumSubCordovaPlugin";

    private static volatile String newAccessToken = null;
    private static SNSMobileSDK.SDK snsSdk;
    private volatile static SNSActionResult actionResultHandlerComplete;

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Log.d(TAG, "execute: " + action + " args: " + args);
        if (action.equals(LAUNCH_ACTION)) {
            if (args.isNull(0)) {
                callbackContext.error("Error: SDK Config object must be provided");
                return false;
            }

            JSONObject conf = args.getJSONObject(0);
            String apiUrl = conf.optString("apiUrl");
            String accessToken = conf.optString("accessToken");
            String locale = conf.optString("locale");
            boolean isDebug = conf.optBoolean("debug", false);
            JSONObject hasHandlers = conf.getJSONObject("hasHandlers");
            JSONObject applicantConf = conf.optJSONObject("applicantConf");
            JSONObject settings = conf.optJSONObject("settings");
            JSONObject theme = conf.optJSONObject("theme");
            String phone = applicantConf.optString("phone");
            String email = applicantConf.optString("email");
            JSONObject strings = conf.optJSONObject("strings");
            JSONObject preferredDocumentDefinitions = conf.getJSONObject("preferredDocumentDefinitions");
            boolean isAnalyticsEnabled = !(conf.has("isAnalyticsEnabled") && !conf.optBoolean("isAnalyticsEnabled"));
            int autoCloseOnApprove = conf.optInt("autoCloseOnApprove", 3);
            boolean isDisableMLKit = conf.optBoolean("disableMLKit", false);

            if (TextUtils.isEmpty(accessToken)) {
                callbackContext.error("Error: Access token must be provided");
                return false;
            }
            if (TextUtils.isEmpty(locale)) {
                locale = Locale.getDefault().getLanguage();
            }
            this.launchSNSMobileSDK(apiUrl, accessToken, email, phone, locale, isDebug, theme, settings, strings, isAnalyticsEnabled, hasHandlers, preferredDocumentDefinitions, autoCloseOnApprove, isDisableMLKit, callbackContext);
            return true;
        } else if (action.equals(NEW_TOKEN_ACTION)) {
            newAccessToken = args.getString(0);
            return true;
        } else if (action.equals(DISMISS_ACTION)) {
            cordova.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    snsSdk.dismiss();
                }
            });
            return true;
        } else if (ACTION_COMPLETED_ACTION.equalsIgnoreCase(action)) {
            String result = args.getJSONObject(0).getString("result");
            actionResultHandlerComplete = "cancel".equalsIgnoreCase(result) ? SNSActionResult.Cancel : SNSActionResult.Continue;
            return true;
        } else {
            callbackContext.error("Method not implemented");
            return false;
        }
    }

    private void requestNewAccessToken() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                webView.getEngine().evaluateJavascript("window.SNSMobileSDK.getNewAccessToken()", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        // no op
                    }
                });
            }
        });
    }

    private void requestActionResult(String actionId, String answer, String actionType, Boolean allowContinuing) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                final String func = "window.SNSMobileSDK.sendEvent('onActionResult', { actionId: '" + actionId + "', answer: '" + answer + "', actionType: '" + actionType + ", allowContinuing: '" + allowContinuing + "})";

                webView.getEngine().evaluateJavascript(func, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String s) {
                        // no op
                    }
                });
            }
        });
    }

    private void launchSNSMobileSDK(
            final String apiUrl,
            final String accessToken,
            String email,
            String phone,
            final String locale,
            final boolean isDebug,
            final JSONObject theme,
            final JSONObject settings,
            final JSONObject strings,
            final boolean isAnalyticsEnabled,
            final JSONObject hasHandlers,
            final JSONObject preferredDocumentDefinitions,
            final int autoCloseOnApprove,
            final boolean isDisableMLKit,
            CallbackContext callbackContext
    ) {

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                try {

                    final SNSActionResultHandler actionResultHandler = hasHandlers.optBoolean("onActionResult") ?
                            (actionId, actionType, answer, allowContinuing) -> {
                                Log.d(TAG, "Calling onActionResult(" + actionId + ", " + answer + ")");
                                actionResultHandlerComplete = null;
                                requestActionResult(actionId, answer, actionType, allowContinuing);
                                int cnt = 0;
                                while (actionResultHandlerComplete == null) {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        //no op
                                    }
                                    if (++cnt > 100) {
                                        return SNSActionResult.Continue;
                                    }
                                }
                                Log.d(TAG, "SumSub: Received: " + actionResultHandlerComplete + ' ' + Thread.currentThread().getName());
                                return actionResultHandlerComplete;
                            } : null;

                    final SNSErrorHandler errorHandler = e -> Log.d(TAG, Log.getStackTraceString(e));

                    final SNSStateChangedHandler stateChangedHandler = (oldState, newState) -> {
                        final String newStatus = newState.getClass().getSimpleName();
                        final String prevStatus = oldState.getClass().getSimpleName();
                        final String func = "window.SNSMobileSDK.sendEvent('onStatusChanged', { newStatus: '" + newStatus + "', prevStatus: '" + prevStatus + "' })";
                        cordova.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                webView.getEngine().evaluateJavascript(func, new ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String s) {
                                        // no op
                                    }
                                });
                            }
                        });
                    };

                    final SNSCompleteHandler completeHandler = (snsCompletionResult, snssdkState) -> getResultToTheClient(snsCompletionResult, snssdkState, callbackContext);

                    final SNSEventHandler eventHandler = snsEvent -> {
                        Map<String, Object> params = new HashMap<>();
                        final Map<String, Object> payload = snsEvent.getPayload();
                        for (String key : payload.keySet()) {
                            if (key.equals("isCanceled") || key.equals("isCancelled")) {
                                params.put("isCancelled", (Boolean) payload.get(key));
                            } else {
                                params.put(key, payload.get(key).toString());
                            }
                        }
                        final String func = "window.SNSMobileSDK.sendEvent('onEvent', { 'eventType': '" + upperCaseFirstLetter(snsEvent.getEventType()) + "', 'payload': " + mapToString(params) + " })";
                        cordova.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                webView.getEngine().evaluateJavascript(func, new ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String s) {
                                        // no op
                                    }
                                });
                            }
                        });
                    };

                    SNSMobileSDK.Builder snsSdkBuilder = new SNSMobileSDK.Builder(cordova.getActivity());

                    if (apiUrl != null && !apiUrl.isEmpty()) {
                        snsSdkBuilder.withBaseUrl(apiUrl);
                    }

                    if (theme != null) {
                        SNSMobileSDK.INSTANCE.isDebug();
                        snsSdkBuilder.withJsonTheme(theme, SNSCustomizationFileFormat.CORDOVA);
                    }

                    if (preferredDocumentDefinitions != null) {
                        Map<String, SNSDocumentDefinition> documents = new HashMap<>();

                        Iterator<String> keys = preferredDocumentDefinitions.keys();
                        while (keys.hasNext()) {
                            try {
                                String key = keys.next();
                                JSONObject data = preferredDocumentDefinitions.getJSONObject(key);
                                String iDocType = null;
                                if (data.has("idDocType")) {
                                    iDocType = data.getString("idDocType");
                                }
                                String country = null;
                                if (data.has("country")) {
                                    country = data.getString("country");
                                }
                                SNSDocumentDefinition documentDefinition = new SNSDocumentDefinition(iDocType, country);
                                documents.put(key, documentDefinition);
                            } catch (Exception e) {
                                Log.e(TAG, "Exception: " + e);
                                callbackContext.error("Error:" + e.getMessage());
                            }
                        }

                        if (!documents.isEmpty()) {
                            snsSdkBuilder.withPreferredDocumentDefinitions(documents);
                        }
                    }

                    List<SNSModule> modules = new ArrayList<>();
                    modules.add(new SNSProoface());
                    if (isDisableMLKit) {
                        modules.add(new SNSCoreModule(SNSCoreModule.FEATURE_DISABLE_MLKIT));
                    }

                    snsSdk = snsSdkBuilder
                            .withAccessToken(accessToken, () -> {
                                Log.d(TAG, "SumSub: calling onTokenExpired!");
                                newAccessToken = null;
                                requestNewAccessToken();
                                int cnt = 0;
                                while (newAccessToken == null) {
                                    try {
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        //no op
                                    }
                                    if (++cnt > 100) {
                                        return null;
                                    }
                                }
                                Log.d(TAG, "SumSub: Received new token: " + newAccessToken + ' ' + Thread.currentThread().getName());
                                return newAccessToken;
                            })
                            .withDebug(isDebug)
                            .withModules(modules)
                            .withErrorHandler(errorHandler)
                            .withStateChangedHandler(stateChangedHandler)
                            .withCompleteHandler(completeHandler)
                            .withActionResultHandler(actionResultHandler)
                            .withEventHandler(eventHandler)
                            .withLocale(new Locale(locale))
                            .withSettings(toMap(settings))
                            .withAnalyticsEnabled(isAnalyticsEnabled)
                            .withConf(new SNSInitConfig(email, phone, strings != null ? toMap(strings) : null))
                            .withAutoCloseOnApprove(autoCloseOnApprove)
                            .build();
                    snsSdk.launch();
                } catch (Exception e) {
                    Log.e(TAG, "Exception: " + e);
                    callbackContext.error("Error:" + e.getMessage());
                }

            }
        });

        cordova.setActivityResultCallback(this);
    }

    private String upperCaseFirstLetter(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private void getResultToTheClient(SNSCompletionResult snsCompletionResult, SNSSDKState snssdkState, CallbackContext callbackContext) {
        if (snsCompletionResult instanceof SNSCompletionResult.SuccessTermination) {
            callbackContext.success(getResult(true, snssdkState, null, null));
        } else if (snsCompletionResult instanceof SNSCompletionResult.AbnormalTermination) {
            SNSCompletionResult.AbnormalTermination abnormalTermination = (SNSCompletionResult.AbnormalTermination) snsCompletionResult;
            String message = abnormalTermination.getException() != null ? abnormalTermination.getException().getMessage() : null;
            if (snssdkState instanceof SNSSDKState.Failed) {
                callbackContext.success(getResult(false, snssdkState, message, snssdkState.getClass().getSimpleName()));
            } else {
                callbackContext.success(getResult(false, new SNSSDKState.Failed.Unknown(new Exception()), message, "Unknown"));
            }
        } else {
            callbackContext.error("Unknown completion result: " + snsCompletionResult.getClass().getName());
        }
    }

    private JSONObject getResult(boolean success, SNSSDKState state, String errorMsg, String errorType) {
        final JSONObject result = new JSONObject();
        try {
            result.put("success", success);
            result.put("status", state != null ? getSDKStateName(state) : "Unknown");
            result.put("errorType", errorType);
            result.put("errorMsg", errorMsg);
            if (state instanceof SNSSDKState.ActionCompleted) {
                final SNSSDKState.ActionCompleted action = (SNSSDKState.ActionCompleted) state;
                final JSONObject actionResult = new JSONObject();
                actionResult.put("actionId", action.getActionId());
                actionResult.put("answer", action.getAnswer());
                result.put("actionResult", actionResult);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private String getSDKStateName(SNSSDKState state) {
        if (state instanceof SNSSDKState.Failed) {
            return "Failed";
        } else {
            return state.getClass().getSimpleName();
        }
    }

    private String mapToString(Map<String, Object> values) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (String key : values.keySet()) {
            Object value = values.get(key);
            sb.append("'");
            sb.append(key);
            sb.append("':  ");
            if (value instanceof String) sb.append("'");
            sb.append(value);
            if (value instanceof String) sb.append("'");
            sb.append(",");
        }
        sb.setLength(sb.length() - 1); // strip last comma
        sb.append('}');
        return sb.toString();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    public static Map<String, String> toMap(JSONObject jsonobj) throws JSONException {
        Map<String, String> map = new HashMap<String, String>();
        Iterator<String> keys = jsonobj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = (String) jsonobj.get(key);
            map.put(key, value);
        }
        return map;
    }


}