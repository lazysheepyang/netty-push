package com.example.demo.data

data class RequestHead(
        var msgType: String,
        var src: String,
        var appKey: String,
        var SN:String,
        var protocolVersion: String,
        var sdkVersion: String,
        var sessionId: String,
        var msgId: String)
