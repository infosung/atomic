package com.infosung.atomic.app.oauth.application.exception

class OauthRedirectRemoteFailureException(
    message: String,
    cause: Throwable? = null,
    errorCode: OauthRedirectErrorCode = OauthRedirectErrorCode.OAUTH_PROVIDER_REMOTE_FAILURE,
) : OauthRedirectApplicationException(errorCode, message, cause)
