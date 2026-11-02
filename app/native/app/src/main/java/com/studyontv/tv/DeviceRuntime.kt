package com.studyontv.tv

import android.content.Context

object DeviceRuntime {
    private var server: LanServer? = null
    lateinit var store: LocalStore
        private set
    lateinit var learning: LearningEngine
        private set

    @Synchronized
    fun ensure(context: Context) {
        if (server != null) return
        val app = context.applicationContext
        (app as android.app.Application).registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: android.app.Activity) {
                Foreground.resumeOwn(activity)
                GateService.onOwnActivityResumed()
            }
            override fun onActivityPaused(activity: android.app.Activity) { Foreground.pauseOwn(activity) }
            override fun onActivityCreated(activity: android.app.Activity, state: android.os.Bundle?) = Unit
            override fun onActivityStarted(activity: android.app.Activity) = Unit
            override fun onActivityStopped(activity: android.app.Activity) = Unit
            override fun onActivitySaveInstanceState(activity: android.app.Activity, state: android.os.Bundle) = Unit
            override fun onActivityDestroyed(activity: android.app.Activity) = Unit
        })
        store = LocalStore(app)
        SeedAssets.ensureQuestions(app, store)
        learning = LearningEngine(store)
        server = LanServer(app, store, app.assets).also {
            it.start()
            Prefs.setLocalPort(app, it.boundPort)
        }
        WebShell.PrefsHolder.baseUrl = Prefs.localBase(app)
        if (GradingService.settings(store).optString("mode") == "ai") {
            val pending = store.reviews("pending")
            for (i in 0 until pending.length()) GradingService.enqueue(store, learning, pending.getJSONObject(i).getString("id"))
        }
    }

    fun connection(context: Context): org.json.JSONObject {
        ensure(context)
        return org.json.JSONObject().put("ip", server?.localIp() ?: "")
            .put("port", Prefs.localPort(context)).put("token", store.adminToken())
    }
}
