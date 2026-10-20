package com.declan.callrecord;

interface ILogCallback {
    void onLogEvent(String level, String tag, String message, String throwableStackTrace);
}