package io.github.howard20181.hyperos.fcmlive

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerExemptionManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.util.Pair
import androidx.annotation.RequiresApi
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Xposed module entry: keeps FCM / GMS wake paths alive on HyperOS.
 *
 * Do not "modernize" away:
 * - Public class extending [XposedModule] with a no-arg constructor (proguard).
 * - Hook callbacks run in system_server / PowerKeeper: never throw out of them,
 *   never block the main thread, never switch to coroutines.
 * - The four FCM marker constants are shared with the settings list.
 */
@SuppressLint("PrivateApi")
class Hooker : XposedModule() {

    private var param: Pair<String, ClassLoader>? = null
    private var systemContext: Context? = null

    /**
     * Counts behind the end-of-install summary line.
     * Every hook goes through [hookE]; absent targets are counted by [logSkip].
     */
    private var hooksInstalled = 0
    private var hookTargetsAbsent = 0

    private fun hookE(executable: Executable): XposedInterface.HookBuilder {
        val builder = hook(executable)
        hooksInstalled++
        if (apiVersion >= 102) {
            builder.setId(executable.toGenericString())
        }
        return builder
    }

    private fun logSkip(message: String) {
        logSkip(message, Log.INFO)
    }

    private fun logSkipOtherGeneration(message: String) {
        logSkip(message, Log.DEBUG)
    }

    private fun logSkip(message: String, level: Int) {
        hookTargetsAbsent++
        log(level, TAG, message)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        val classLoader = param.classLoader
        this.param = Pair.create("system", classLoader)
        try {
            hookSystemServer(classLoader)
        } catch (tr: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook SystemServer", tr)
        }
        logSummary("system_server")
    }

    private fun logSummary(process: String) {
        log(
            Log.INFO, TAG, "HyperFCMLive active in $process: " +
                "$hooksInstalled hook(s) installed, " +
                "$hookTargetsAbsent target(s) absent on this ROM"
        )
    }

    private fun hookSystemServer(classLoader: ClassLoader) {
        try {
            hookAllowlist()
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook allowlist receiver", t)
        }
        try {
            hookGreezeManagerService(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook GreezeManagerService", t)
        }
        try {
            hookGreezerNoRestrict(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook GreezerNoRestrict", t)
        }
        try {
            hookDomesticPolicyManager(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook DomesticPolicyManager", t)
        }
        try {
            hookListAppsManager(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook ListAppsManager", t)
        }
        try {
            hookBroadcastQueueModernStubImpl(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook BroadcastQueueModernStubImpl", t)
        }
        try {
            hookGreezeBroadcastCache(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook greeze broadcast cache", t)
        }
        try {
            hookProcessPolicy(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook ProcessPolicy", t)
        }
        try {
            hookAwareResourceControl(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook AwareResourceControl", t)
        }
        try {
            hookSleepModeNetworkPolicy(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook sleep-mode network policy", t)
        }
        try {
            hookActivityManagerService(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook ActivityManagerService", t)
        }
        try {
            hookInternationalPolicyManager(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook InternationalPolicyManager", t)
        }
        try {
            hookProcessCleanerBase(classLoader)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook ProcessCleanerBase", t)
        }
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (!param.isFirstPackage) return
        val packageName = param.packageName
        val classLoader = param.classLoader
        this.param = Pair.create(packageName, classLoader)
        try {
            hookPackage(packageName, classLoader)
        } catch (tr: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook package", tr)
        }
        logSummary(packageName)
    }

    private fun hookPackage(packageName: String, classLoader: ClassLoader) {
        if ("com.miui.powerkeeper" == packageName) {
            try {
                hookGmsObserver(classLoader)
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook GmsObserver", t)
            }
            try {
                hookAppStandbyUidState(packageName, classLoader)
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook AppStandbyController", t)
            }
            try {
                hookGlobalFeatureConfigureHelper(classLoader)
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook GlobalFeatureConfigureHelper", t)
            }
            try {
                hookNoRestrictList(classLoader)
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook NoRestrictList", t)
            }
            try {
                hookScenarioCompiler(classLoader)
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to hook ScenarioCompiler", t)
            }
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        log(Log.WARN, TAG, "Hot reload requested — a full reboot is recommended for reliability")
        param.setSavedInstanceState(this.param)
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        param.oldHookHandles.forEach { h ->
            try {
                h.unhook()
            } catch (ignored: Throwable) {
            }
        }
        val saved = param.savedInstanceState
        if (saved is Pair<*, *>) {
            val packageName = saved.first as? String
            val classLoader = saved.second as? ClassLoader
            if (packageName != null && classLoader != null) {
                this.param = Pair.create(packageName, classLoader)
                try {
                    if (param.isSystemServer) {
                        hookSystemServer(classLoader)
                    } else {
                        hookPackage(packageName, classLoader)
                    }
                } catch (tr: Throwable) {
                    log(Log.ERROR, TAG, "Hot reload failed", tr)
                }
            }
        }
    }

    private fun hookGreezeManagerService(classLoader: ClassLoader) {
        val GreezeManagerServiceClass =
            classLoader.loadClass("com.miui.server.greeze.GreezeManagerService")
        try {
            val isAllowBroadcastMethod = findMethod(
                GreezeManagerServiceClass,
                "isAllowBroadcast",
                Int::class.javaPrimitiveType, String::class.java,
                Int::class.javaPrimitiveType, String::class.java, String::class.java
            )
            val getPackageNameFromUidMethod = findMethod(
                GreezeManagerServiceClass,
                "getPackageNameFromUid",
                Int::class.javaPrimitiveType
            )
            getPackageNameFromUidMethod?.isAccessible = true
            if (getPackageNameFromUidMethod == null) {
                log(
                    Log.INFO, TAG,
                    "GreezeManagerService#getPackageNameFromUid absent;" +
                        " isAllowBroadcast falls back to the raw callee argument"
                )
            }
            if (isAllowBroadcastMethod == null) {
                log(Log.ERROR, TAG, "GreezeManagerService#isAllowBroadcast absent, skip")
            } else {
                val uidLookup = getPackageNameFromUidMethod
                hookE(isAllowBroadcastMethod).intercept { chain: XposedInterface.Chain ->
                    var calleePkgName: String? = chain.getArg(3) as? String
                    if (uidLookup != null) {
                        try {
                            val calleeUid = chain.getArg(2)
                            if (calleeUid is Int) {
                                val calleePackageName =
                                    getInvoker(uidLookup).invoke(chain.thisObject, calleeUid)
                                if (calleePackageName is String) {
                                    calleePkgName = calleePackageName
                                }
                            }
                        } catch (e: Exception) {
                            log(Log.ERROR, TAG, "Failed to get callee package name", e)
                        }
                    }
                    val action = chain.getArg(4)
                    if (action is String &&
                        (((chain.getArg(1) as? String)?.let {
                            GMS_PACKAGE_NAME == it &&
                                ACTION_REMOTE_INTENT == action &&
                                shouldApply(calleePkgName)
                        } == true) ||
                            ((GMS_PACKAGE_NAME == calleePkgName ||
                                GMS_PERSISTENT_PROCESS_NAME == calleePkgName) &&
                                CN_DEFER_BROADCAST.contains(action)))
                    ) {
                        return@intercept true
                    }
                    chain.proceed()
                }
                deoptimize(isAllowBroadcastMethod)
            }
        } catch (e: Exception) {
            log(Log.ERROR, TAG, "Failed to hook GreezeManagerService#isAllowBroadcast", e)
        }
        try {
            val deferBroadcastForMiuiMethod = GreezeManagerServiceClass.getDeclaredMethod(
                "deferBroadcastForMiui", String::class.java
            )
            hookE(deferBroadcastForMiuiMethod).intercept { chain: XposedInterface.Chain ->
                if ((chain.getArg(0) as? String)?.let { CN_DEFER_BROADCAST.contains(it) } == true) {
                    return@intercept false
                }
                chain.proceed()
            }
            deoptimize(deferBroadcastForMiuiMethod)
        } catch (e: Exception) {
            log(Log.ERROR, TAG, "Failed to hook GreezeManagerService#deferBroadcastForMiui", e)
        }
        val triggerGMSLimitActionMethod: Method
        try {
            triggerGMSLimitActionMethod = try {
                GreezeManagerServiceClass.getDeclaredMethod(
                    "triggerGMSLimitAction", Boolean::class.javaPrimitiveType
                )
            } catch (ignored: NoSuchMethodException) {
                GreezeManagerServiceClass.getDeclaredMethod("triggerGMSLimitAction")
            }
            hookE(triggerGMSLimitActionMethod).intercept { chain: XposedInterface.Chain ->
                if (chain.args.isNotEmpty()) {
                    val args = chain.args.toTypedArray()
                    args[0] = false
                    return@intercept chain.proceed(args)
                }
                try {
                    val mGmsLimitEnabled =
                        GreezeManagerServiceClass.getDeclaredField("mGmsLimitEnabled")
                    UnsafeUtils.setBooleanField(mGmsLimitEnabled, chain.thisObject, false)
                } catch (t: Throwable) {
                    log(Log.ERROR, TAG, "Failed to clear mGmsLimitEnabled", t)
                }
                chain.proceed()
            }
            deoptimize(triggerGMSLimitActionMethod)
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook GreezeManagerService#triggerGMSLimitAction", e)
        }
        try {
            val updateGmsNetStatusMethod = GreezeManagerServiceClass.getDeclaredMethod(
                "updateGmsNetStatus", Boolean::class.javaPrimitiveType
            )
            hookE(updateGmsNetStatusMethod).intercept { chain: XposedInterface.Chain ->
                val args = chain.args.toTypedArray()
                if (args.isNotEmpty()) {
                    args[0] = false
                }
                chain.proceed(args)
            }
            deoptimize(updateGmsNetStatusMethod)
        } catch (e: NoSuchMethodException) {
            logSkip("GreezeManagerService#updateGmsNetStatus absent, skip")
        }
    }

    private fun hookDomesticPolicyManager(classLoader: ClassLoader) {
        val DomesticPolicyManagerClass =
            classLoader.loadClass("com.miui.server.greeze.DomesticPolicyManager")
        val deferBroadcastMethod = DomesticPolicyManagerClass.getDeclaredMethod(
            "deferBroadcast", String::class.java
        )
        hookE(deferBroadcastMethod).intercept { chain: XposedInterface.Chain ->
            // Bypass deferral for the complete FCM chain:
            //  - CN_DEFER_BROADCAST: GMS-internal reconnect/heartbeat actions
            //  - ACTION_REMOTE_INTENT: GMS → target-app c2dm delivery
            // Everything else follows the normal deferral policy (aligned with
            // strict mode's minimal-intervention philosophy).
            val action = chain.getArg(0) as? String
            if (action != null &&
                (CN_DEFER_BROADCAST.contains(action) || ACTION_REMOTE_INTENT == action)
            ) {
                return@intercept false
            }
            chain.proceed()
        }
        deoptimize(deferBroadcastMethod)
    }

    private fun hookListAppsManager(classLoader: ClassLoader) {
        val ListAppsManagerClass =
            classLoader.loadClass("com.miui.server.greeze.power.ListAppsManager")
        var mSystemBlackListField: Field? = null
        try {
            mSystemBlackListField = ListAppsManagerClass.getDeclaredField("mSystemBlackList")
        } catch (e: NoSuchFieldException) {
            try {
                mSystemBlackListField = ListAppsManagerClass.getDeclaredField("SYSTEM_BLACK_LIST")
            } catch (ex: NoSuchFieldException) {
                log(
                    Log.ERROR, TAG,
                    "Failed to find ListAppsManager.mSystemBlackList or ListAppsManager.SYSTEM_BLACK_LIST",
                    e
                )
            }
        }
        if (mSystemBlackListField != null) {
            mSystemBlackListField.isAccessible = true
            val constructors = ListAppsManagerClass.declaredConstructors
            for (constructor in constructors) {
                val field = mSystemBlackListField
                hookE(constructor).intercept { chain: XposedInterface.Chain ->
                    try {
                        chain.proceed()
                    } finally {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val mSystemBlackList =
                                field.get(chain.thisObject) as MutableList<String>?
                            mSystemBlackList?.remove(GMS_PACKAGE_NAME)
                        } catch (e: Exception) {
                            log(Log.ERROR, TAG, "Failed to modify system blacklist", e)
                        }
                    }
                }
                deoptimize(constructor)
            }
        }
        try {
            val isInWhiteListMethod = ListAppsManagerClass.getDeclaredMethod(
                "isInWhiteList", String::class.java
            )
            var mUseDataWhiteListField: Field? = null
            try {
                mUseDataWhiteListField = ListAppsManagerClass.getDeclaredField("mUseDataWhiteList")
            } catch (e: NoSuchFieldException) {
                try {
                    mUseDataWhiteListField =
                        ListAppsManagerClass.getDeclaredField("USE_DATA_WHITE_LIST")
                } catch (ex: NoSuchFieldException) {
                    log(
                        Log.ERROR, TAG,
                        "Failed to find ListAppsManager.mUseDataWhiteList or ListAppsManager.USE_DATA_WHITE_LIST",
                        e
                    )
                }
            }
            if (mUseDataWhiteListField != null) {
                mUseDataWhiteListField.isAccessible = true
                val field = mUseDataWhiteListField
                hookE(isInWhiteListMethod).intercept { chain: XposedInterface.Chain ->
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val mUseDataWhiteList =
                            field.get(chain.thisObject) as MutableSet<String>?
                        mUseDataWhiteList?.add(GMS_PACKAGE_NAME)
                    } catch (e: Exception) {
                        log(Log.ERROR, TAG, "Failed to modify use data whitelist", e)
                    }
                    chain.proceed()
                }
            }
        } catch (e: NoSuchMethodException) {
            log(Log.ERROR, TAG, "Failed to hook ListAppsManager#isInWhiteList", e)
        }
    }

    private fun hookBroadcastQueueModernStubImpl(classLoader: ClassLoader) {
        val BroadcastQueueModernStubImplClass =
            classLoader.loadClass("com.android.server.am.BroadcastQueueModernStubImpl")
        val BroadcastQueueClass = classLoader.loadClass("com.android.server.am.BroadcastQueue")
        val BroadcastRecordClass = classLoader.loadClass("com.android.server.am.BroadcastRecord")
        val callerPackageField = BroadcastRecordClass.getDeclaredField("callerPackage")
        callerPackageField.isAccessible = true
        val intentField = BroadcastRecordClass.getDeclaredField("intent")
        intentField.isAccessible = true
        val checkApplicationAutoStartMethod = BroadcastQueueModernStubImplClass.getDeclaredMethod(
            "checkApplicationAutoStart",
            BroadcastQueueClass,
            BroadcastRecordClass,
            ResolveInfo::class.java
        )
        hookE(checkApplicationAutoStartMethod).intercept { chain: XposedInterface.Chain ->
            try {
                val broadcastRecord = chain.getArg(1)
                val callerPackage = callerPackageField.get(broadcastRecord) as? String
                val intent = intentField.get(broadcastRecord) as? Intent
                val targetPackage = intent?.let { targetPackageOf(it) }
                if (callerPackage != null &&
                    GMS_PACKAGE_NAME == callerPackage &&
                    intent != null &&
                    ACTION_REMOTE_INTENT == intent.action &&
                    targetPackage != null &&
                    shouldWake(targetPackage)
                ) {
                    return@intercept true
                }
            } catch (e: Exception) {
                log(
                    Log.ERROR, TAG,
                    "Failed to modify BroadcastQueueModernStubImpl#checkApplicationAutoStart", e
                )
            }
            chain.proceed()
        }
        deoptimize(checkApplicationAutoStartMethod)

        // Second greeze gate. checkApplicationAutoStart only covers the cold-start
        // (ResolveInfo) path; a warm but frozen receiver goes through this one, which
        // asks GreezeManagerService#isRestrictReceiver. An earlier revision
        // short-circuited BroadcastQueueModernStubImpl#checkReceiverIfRestricted, which
        // skipped the thawUidAsync("bc_action") that isRestrictReceiver performs on its
        // native pass-through path: the broadcast was dispatched to a still-frozen
        // process, no one ever thawed it, and GMS retried the same message forever
        // ("No response to broadcast"). Hook isRestrictReceiver itself instead — answer
        // false (not restricted) and reproduce the native thaw before delivering.
        try {
            val GreezeManagerServiceClass =
                classLoader.loadClass("com.miui.server.greeze.GreezeManagerService")
            val isRestrictReceiverMethod = GreezeManagerServiceClass.getDeclaredMethod(
                "isRestrictReceiver",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val thawUidAsyncMethod = GreezeManagerServiceClass.getDeclaredMethod(
                "thawUidAsync",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            hookE(isRestrictReceiverMethod).intercept { chain: XposedInterface.Chain ->
                try {
                    val intent = chain.getArg(0) as? Intent
                    val callerPackage = chain.getArg(2) as? String
                    val calleeUid = chain.getArg(3) as Int
                    val calleePackage = chain.getArg(4) as? String
                    if (GMS_PACKAGE_NAME == callerPackage &&
                        intent != null &&
                        ACTION_REMOTE_INTENT == intent.action &&
                        shouldWake(calleePackage)
                    ) {
                        // Same reason string and caller uid the native pass-through
                        // path uses, so greeze bookkeeping stays consistent.
                        thawUidAsyncMethod.invoke(chain.thisObject, calleeUid, 1000, "bc_action")
                        return@intercept false
                    }
                } catch (e: Exception) {
                    log(
                        Log.ERROR, TAG,
                        "Failed to modify GreezeManagerService#isRestrictReceiver", e
                    )
                }
                chain.proceed()
            }
            deoptimize(isRestrictReceiverMethod)
        } catch (e: NoSuchMethodException) {
            logSkip("GreezeManagerService#isRestrictReceiver absent, skip")
        } catch (e: ClassNotFoundException) {
            logSkip("GreezeManagerService absent, isRestrictReceiver not hooked")
        }
    }

    /**
     * Stops greeze from parking a c2dm broadcast instead of delivering it.
     *
     * GreezeManagerService#isNeedCachedBroadcast(Intent, int uid, String pkg) runs after
     * the receiver has been found frozen and returns true to mean "cache this broadcast
     * and replay it once the target thaws". That is the mechanism behind a broadcast
     * showing up as delivered in the AMS log while the app stays silent until the next
     * unlock. Answering false for c2dm keeps the normal delivery path.
     *
     * The uid argument is deliberately unused: it identifies the frozen receiver, and the
     * allowlist the user configured is expressed in package names.
     */
    private fun hookGreezeBroadcastCache(classLoader: ClassLoader) {
        try {
            val GreezeManagerServiceClass =
                classLoader.loadClass("com.miui.server.greeze.GreezeManagerService")
            val isNeedCachedBroadcastMethod = GreezeManagerServiceClass.getDeclaredMethod(
                "isNeedCachedBroadcast",
                Intent::class.java,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            hookE(isNeedCachedBroadcastMethod).intercept { chain: XposedInterface.Chain ->
                try {
                    val intent = chain.getArg(0) as? Intent
                    val packageName = chain.getArg(2) as? String
                    if (intent != null &&
                        ACTION_REMOTE_INTENT == intent.action &&
                        shouldWake(packageName)
                    ) {
                        return@intercept false
                    }
                } catch (e: Exception) {
                    log(
                        Log.ERROR, TAG,
                        "Failed to modify GreezeManagerService#isNeedCachedBroadcast", e
                    )
                }
                chain.proceed()
            }
            deoptimize(isNeedCachedBroadcastMethod)
        } catch (e: ClassNotFoundException) {
            logSkip("GreezeManagerService absent, broadcast cache not hooked")
        } catch (e: NoSuchMethodException) {
            logSkip("GreezeManagerService#isNeedCachedBroadcast absent, skip")
        }
    }

    private fun hookProcessPolicy(classLoader: ClassLoader) {
        val ProcessPolicyClass = classLoader.loadClass("com.android.server.am.ProcessPolicy")
        val getWhiteListMethod = ProcessPolicyClass.getDeclaredMethod(
            "getWhiteList", Int::class.javaPrimitiveType
        )
        hookE(getWhiteListMethod).intercept { chain: XposedInterface.Chain ->
            val result = chain.proceed()
            try {
                val flags = chain.getArg(0)
                if (flags is Int && (flags and 1) != 0 && result is List<*>) {
                    val source = result
                    val whiteList = ArrayList<Any?>(source)
                    addIfAbsent(whiteList, GMS_PACKAGE_NAME)
                    addIfAbsent(whiteList, GMS_PERSISTENT_PROCESS_NAME)
                    addIfAbsentInPlace(source, GMS_PACKAGE_NAME)
                    addIfAbsentInPlace(source, GMS_PERSISTENT_PROCESS_NAME)
                    return@intercept whiteList
                }
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to extend ProcessPolicy white list", t)
            }
            result
        }
    }

    private fun addIfAbsent(list: MutableList<Any?>, value: String) {
        if (!list.contains(value)) {
            list.add(value)
        }
    }

    private fun addIfAbsentInPlace(target: List<*>?, value: String) {
        if (target == null || target.contains(value)) {
            return
        }
        try {
            @Suppress("UNCHECKED_CAST")
            (target as MutableList<Any?>).add(value)
        } catch (ignored: Throwable) {
        }
    }

    private fun hookAwareResourceControl(classLoader: ClassLoader) {
        val AwareResourceControlClass =
            classLoader.loadClass("com.miui.server.greeze.power.AwareResourceControl")
        val mNoNetworkBlackUidsField =
            AwareResourceControlClass.getDeclaredField("mNoNetworkBlackUids")
        mNoNetworkBlackUidsField.isAccessible = true
        for (constructor in AwareResourceControlClass.declaredConstructors) {
            hookE(constructor).intercept { chain: XposedInterface.Chain ->
                try {
                    chain.proceed()
                } finally {
                    try {
                        pruneGmsFromNoNetworkBlacklist(mNoNetworkBlackUidsField, chain.thisObject)
                    } catch (t: Throwable) {
                        log(
                            Log.ERROR, TAG,
                            "Failed to modify AwareResourceControl.mNoNetworkBlackUids", t
                        )
                    }
                }
            }
            deoptimize(constructor)
        }
    }

    @Volatile
    private var noNetworkBlacklistMismatchLogged = false

    private fun pruneGmsFromNoNetworkBlacklist(blacklistField: Field, awareResourceControl: Any) {
        val raw = blacklistField.get(awareResourceControl)
        if (raw !is Collection<*>) {
            return
        }
        @Suppress("UNCHECKED_CAST")
        val blacklist = raw as MutableCollection<Any?>
        val removedByName = blacklist.remove(GMS_PACKAGE_NAME)
        val uid = gmsUid()
        val removedByUid = uid != null && blacklist.remove(uid)
        if (removedByUid || removedByName) {
            log(
                Log.INFO, TAG, "Removed GMS from NoNetworkBlackUids (by " +
                    (if (removedByUid) "uid $uid" else "package name") + ")"
            )
        } else if (!noNetworkBlacklistMismatchLogged) {
            noNetworkBlacklistMismatchLogged = true
            log(
                Log.INFO, TAG, "NoNetworkBlackUids (size=" + blacklist.size +
                    ") matched neither the GMS package name nor its uid" +
                    (if (uid == null) " (uid not resolvable yet)" else "")
            )
        }
    }

    private fun gmsUid(): Int? {
        return try {
            val context = getSystemContext() ?: return null
            context.packageManager.getApplicationInfo(GMS_PACKAGE_NAME, 0).uid
        } catch (t: Throwable) {
            null
        }
    }

    private fun getSystemContext(): Context? {
        if (systemContext == null) {
            try {
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val currentApplication = activityThreadClass.getMethod("currentApplication")
                val ctx = currentApplication.invoke(null)
                if (ctx is Context) {
                    systemContext = ctx
                }
            } catch (ignored: Throwable) {
            }
        }
        return systemContext
    }

    /**
     * MIUI 睡眠模式（PhoneSleepModeController）入睡后会打开一条断网链，
     * 只放行 `sleep_mode_network_white_apps` 名单中的应用，其余整夜掐网。
     * GMS 默认不在名单里，于是 FCM 长连接被静默切断 —— 表现就是
     * "FCM 以为只是网络断了"，直到心跳超时重连才恢复。
     *
     * 进入睡眠时 $45 广播接收器的顺序是：
     *   setSleepModeWhitelistUidRules()   // 对 mSleepModeWhitelistUids 逐个下发 added=true
     *   enableSleepModeChain(true)        // 打开断网链
     * 所以只需在下发之前把 GMS 塞进名单，无需改动链开关的语义；
     * 退出时 clearSleepModeWhitelistUidRules() 会对称撤销，不会留下残留规则。
     */
    private fun hookSleepModeNetworkPolicy(classLoader: ClassLoader) {
        val serviceClass = try {
            classLoader.loadClass("com.android.server.net.MiuiNetworkPolicyManagerService")
        } catch (e: ClassNotFoundException) {
            logSkip("MiuiNetworkPolicyManagerService class absent, skip")
            return
        }
        val whitelistField = try {
            serviceClass.getDeclaredField("mSleepModeWhitelistUids")
        } catch (e: NoSuchFieldException) {
            logSkip("MiuiNetworkPolicyManagerService.mSleepModeWhitelistUids absent, skip")
            return
        }
        val applyMethod = try {
            serviceClass.getDeclaredMethod("setSleepModeWhitelistUidRules")
        } catch (e: NoSuchMethodException) {
            logSkipOtherGeneration(
                "MiuiNetworkPolicyManagerService#setSleepModeWhitelistUidRules absent, skip"
            )
            return
        }
        whitelistField.isAccessible = true
        applyMethod.isAccessible = true
        hookE(applyMethod).intercept { chain: XposedInterface.Chain ->
            try {
                addGmsToSleepModeWhitelist(whitelistField, chain.thisObject)
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to extend sleep-mode network whitelist", t)
            }
            chain.proceed()
        }
        deoptimize(applyMethod)
        log(Log.INFO, TAG, "Sleep-mode network whitelist hooked: GMS will stay online overnight")

        // Belt and braces: if GMS did lose its connection overnight (older ROM
        // without the whitelist hook above, or the rule never reached netd),
        // the socket is stale by the time the chain comes down. Nudge GMS to
        // drop it and reconnect instead of waiting for the next heartbeat.
        val chainMethod = try {
            serviceClass.getDeclaredMethod(
                "enableSleepModeChain", Boolean::class.javaPrimitiveType
            )
        } catch (e: NoSuchMethodException) {
            logSkipOtherGeneration(
                "MiuiNetworkPolicyManagerService#enableSleepModeChain absent, skip"
            )
            return
        }
        chainMethod.isAccessible = true
        hookE(chainMethod).intercept { chain: XposedInterface.Chain ->
            val enabling = chain.getArg(0) == true
            chain.proceed()
            if (!enabling) {
                log(Log.INFO, TAG, "Sleep mode exited: network restored, nudging GMS to reconnect")
                val context = getSystemContext()
                if (context != null) {
                    Thread { recoverGmsConnection(context) }.start()
                }
            }
        }
        deoptimize(chainMethod)
    }

    /**
     * Adds GMS to the "keep network during sleep mode" set. Called on the
     * service's own handler thread, right before the rules are pushed to
     * ConnectivityManager, so no other thread observes a half-updated set.
     */
    private fun addGmsToSleepModeWhitelist(whitelistField: Field, owner: Any) {
        val raw = whitelistField.get(owner)
        if (raw !is MutableCollection<*>) {
            return
        }
        @Suppress("UNCHECKED_CAST")
        val whitelist = raw as MutableCollection<Any?>
        val uid = gmsUid() ?: return
        if (whitelist.add(uid)) {
            log(
                Log.INFO, TAG,
                "Sleep mode entering: kept GMS (uid $uid) on the network whitelist"
            )
        }
    }

    private fun hookForceFalse(owner: Class<*>, name: String) {
        try {
            val method = owner.getDeclaredMethod(name, Boolean::class.javaPrimitiveType)
            hookE(method).intercept { chain: XposedInterface.Chain ->
                val args = chain.args.toTypedArray()
                args[0] = false
                chain.proceed(args)
            }
            deoptimize(method)
        } catch (e: NoSuchMethodException) {
            logSkipOtherGeneration("GmsObserver#$name absent, skip")
        }
    }

    private fun hookGmsObserver(classLoader: ClassLoader) {
        try {
            val NetdExecutorClass = classLoader.loadClass("com.miui.powerkeeper.utils.NetdExecutor")
            try {
                val initGmsChainMethod = NetdExecutorClass.getDeclaredMethod(
                    "initGmsChain",
                    String::class.java, Int::class.javaPrimitiveType, String::class.java
                )
                hookE(initGmsChainMethod).intercept { chain: XposedInterface.Chain ->
                    val args = chain.args.toTypedArray()
                    args[2] = "ACCEPT"
                    chain.proceed(args)
                }
                deoptimize(initGmsChainMethod)
            } catch (e: NoSuchMethodException) {
                logSkipOtherGeneration("NetdExecutor#initGmsChain absent, skip")
            }
            try {
                val setGmsDnsBlockerStateMethod = NetdExecutorClass.getDeclaredMethod(
                    "setGmsDnsBlockerState",
                    Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
                )
                hookE(setGmsDnsBlockerStateMethod).intercept { chain: XposedInterface.Chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size > 1) {
                        args[1] = false
                    }
                    chain.proceed(args)
                }
                deoptimize(setGmsDnsBlockerStateMethod)
            } catch (e: NoSuchMethodException) {
                logSkip("NetdExecutor#setGmsDnsBlockerState absent, skip")
            }
            try {
                val setGmsChainStateMethod = NetdExecutorClass.getDeclaredMethod(
                    "setGmsChainState",
                    String::class.java, Boolean::class.javaPrimitiveType
                )
                hookE(setGmsChainStateMethod).intercept { chain: XposedInterface.Chain ->
                    val args = chain.args.toTypedArray()
                    // NetdExecutor.setGmsChainState(chain, enable): enable==true 下发
                    // "set_chain_state <chain> enable"，配合 initGmsChain(gms_wall, uid, "REJECT")
                    // 即 true=开墙阻断 GMS；与 setGmsDnsBlockerState(true→"deny") 一致。
                    if (args.size > 1) {
                        args[1] = false
                    }
                    chain.proceed(args)
                }
                deoptimize(setGmsChainStateMethod)
            } catch (e: NoSuchMethodException) {
                logSkipOtherGeneration("NetdExecutor#setGmsChainState absent, skip")
            }
            try {
                val executeMethod = NetdExecutorClass.getDeclaredMethod(
                    "execute",
                    Int::class.javaPrimitiveType, String::class.java, String::class.java,
                    Array<Any>::class.java
                )
                val executeReturn = executeMethod.returnType
                val skipValue = skipValueFor(executeReturn)
                hookE(executeMethod).intercept { chain: XposedInterface.Chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size >= 4 && args[2] is String && args[3] is Array<*>) {
                        val cmd = args[2] as String
                        @Suppress("UNCHECKED_CAST")
                        val cmdArgs = args[3] as Array<Any?>
                        if ("setuiddnsrule" == cmd && cmdArgs.size >= 2) {
                            val rewritten = cmdArgs.copyOf()
                            rewritten[1] = "allow"
                            args[3] = rewritten
                            return@intercept chain.proceed(args)
                        }
                        if ("enablemiuistandby" == cmd && cmdArgs.isNotEmpty() &&
                            "enable" == cmdArgs[0].toString()
                        ) {
                            return@intercept skipValue
                        }
                    }
                    chain.proceed()
                }
                deoptimize(executeMethod)
                log(
                    Log.INFO, TAG, "NetdExecutor#execute returns " + executeReturn.name +
                        "; standby-firewall skip returns " + (skipValue?.toString() ?: "null")
                )
            } catch (e: NoSuchMethodException) {
                logSkip("NetdExecutor#execute not found, skip command-level GMS net hooks")
            }
        } catch (e: ClassNotFoundException) {
            log(Log.ERROR, TAG, "Failed to hook NetdExecutor", e)
        }
        try {
            val GmsObserverClass = classLoader.loadClass("com.miui.powerkeeper.utils.GmsObserver")
            for (legacyName in arrayOf(
                "updateGmsAlarm", "updateGmsNetWork", "updateGoogleReletivesWakelock"
            )) {
                hookForceFalse(GmsObserverClass, legacyName)
            }
            for (alwaysSkip in arrayOf("disableGms", "disableGmsApps")) {
                try {
                    val disableMethod = GmsObserverClass.getDeclaredMethod(alwaysSkip)
                    hookE(disableMethod).intercept { _: XposedInterface.Chain -> null }
                    deoptimize(disableMethod)
                } catch (e: NoSuchMethodException) {
                    logSkipOtherGeneration("GmsObserver#$alwaysSkip absent, skip")
                }
            }
            for (limitFlag in arrayOf("updateGmsEnabled", "updateGmsState", "updateGmsInstalled")) {
                hookForceFalse(GmsObserverClass, limitFlag)
            }
            try {
                val updateFrameworkGmsNetStatusMethod = GmsObserverClass.getDeclaredMethod(
                    "updateFrameworkGmsNetStatus", Boolean::class.javaPrimitiveType
                )
                hookE(updateFrameworkGmsNetStatusMethod).intercept { chain: XposedInterface.Chain ->
                    val args = chain.args.toTypedArray()
                    if (args.isNotEmpty() && java.lang.Boolean.TRUE == args[0]) {
                        args[0] = false
                    }
                    chain.proceed(args)
                }
                deoptimize(updateFrameworkGmsNetStatusMethod)
            } catch (e: NoSuchMethodException) {
                logSkip("GmsObserver#updateFrameworkGmsNetStatus absent, skip")
            }
            try {
                val onGoogleReachabilityChangedMethod = GmsObserverClass.getDeclaredMethod(
                    "onGoogleReachabilityChanged", Boolean::class.javaPrimitiveType
                )
                hookE(onGoogleReachabilityChangedMethod).intercept { chain: XposedInterface.Chain ->
                    val args = chain.args.toTypedArray()
                    args[0] = true
                    chain.proceed(args)
                }
                deoptimize(onGoogleReachabilityChangedMethod)
            } catch (e: NoSuchMethodException) {
                logSkip("GmsObserver#onGoogleReachabilityChanged absent, skip")
            }
            try {
                val bridgeMethod = GmsObserverClass.getDeclaredMethod(
                    "c", GmsObserverClass, Boolean::class.javaPrimitiveType
                )
                hookE(bridgeMethod).intercept { chain: XposedInterface.Chain ->
                    val args = chain.args.toTypedArray()
                    args[1] = true
                    chain.proceed(args)
                }
                deoptimize(bridgeMethod)
            } catch (ignored: NoSuchMethodException) {
            }
        } catch (e: ClassNotFoundException) {
            log(Log.ERROR, TAG, "Failed to hook GmsObserver", e)
        }
        var disconnectHooked = false
        for (i in 1..8) {
            if (disconnectHooked) break
            val listenerName = "com.miui.powerkeeper.utils.GmsObserver\$$i"
            try {
                val GmsObserverListenerClass = classLoader.loadClass(listenerName)
                try {
                    val disconnectMethod =
                        GmsObserverListenerClass.getDeclaredMethod("googleNetworkDisconnect")
                    hookE(disconnectMethod).intercept { _: XposedInterface.Chain -> null }
                    deoptimize(disconnectMethod)
                    disconnectHooked = true
                    log(Log.INFO, TAG, listenerName + "#googleNetworkDisconnect hooked")
                } catch (ignored: NoSuchMethodException) {
                }
            } catch (ignored: ClassNotFoundException) {
            }
        }
        if (!disconnectHooked) {
            logSkip("GmsObserver\$*#googleNetworkDisconnect absent, skip disconnect rewrite")
        }
    }

    /**
     * Keep GMS out of PowerKeeper's per-uid network restriction set.
     *
     * AppStandbyController.setUidState(int uid, boolean allow) is where the
     * per-uid decision is made. The second parameter really is named "allow"
     * — the method prints "setUidState, uid = %d allow = %b" itself. Its body
     * stores the value into mUidState and then drives DeviceIdlePolicyHelper,
     * so this single call is the convergence point for standby restriction.
     * The downstream helper method is obfuscated and its name differs per
     * ROM generation — OS3 calls s:(IZ)V, OS4 calls r:(IZ)V (both classes also
     * carry the other letter with a different signature). It has exactly one
     * call site in either generation, so no second in-process path can
     * restrict GMS behind setUidState's back. Do not hard-code the letter:
     * only setUidState itself is hooked, and its (IZ)V signature is stable.
     *
     * Only the argument is rewritten. Do not pre-seed mUidState to true: when
     * the incoming value equals the cached one the method returns early, so a
     * true cache would suppress the recovery path instead of triggering it.
     * Forcing allow=true lets the method converge: a restriction attempt sees
     * allow(true) differ from the cached false, then writes true and lifts the
     * restriction. If the divergence ever needs fixing, the safe direction is
     * to force the cache to false, never true — false guarantees the branch
     * actually executes.
     *
     * Not a fix by itself: calling setUidState(gmsUid, true) from outside does
     * not bypass the early return — the early return lives inside that same
     * method, and a cache of true makes the call a no-op, which is exactly the
     * divergent case it would be meant to repair. It also cannot converge the
     * cache with reality: mUidState only consults external state on the first
     * seeding (getUidState), afterwards it is write-only. The only working
     * form is the pair — force the cache to false, then invoke setUidState
     * (uid, true) so the body runs end to end. Because that body also fires
     * sendConnectivityActionToApp(uid), doing it on a timer means waking GMS
     * repeatedly; if it is ever added, drive it from events (module load,
     * screen-on, the pre-flight we already run before delivering a broadcast)
     * with a long minimum interval, never from a periodic tick.
     *
     * That divergence stays unimplemented on purpose: every observable signal
     * on the test device says it is not happening (dumpsys netpolicy shows
     * UID 10133 as policy=4 ALLOW_METERED_BACKGROUND with background
     * restriction off, and GMS is present in all three DeviceIdle whitelist
     * sections), so adding reflexive cache writes would be speculative risk.
     *
     * Known residual gap: if GMS gets restricted out-of-band (never through
     * setUidState) while mUidState still reads true, even an allow=true call
     * short-circuits and nothing lifts the block. P4 recovery does not cover
     * it — it is an outbound "please reconnect" nudge to GMS/GSF, not a lift of
     * a uid restriction, and it only fires on sleep-mode exit or a
     * MILLET_NO_RESTRICT_APP repair, neither of which recurs on its own.
     */
    private fun hookAppStandbyUidState(packageName: String, classLoader: ClassLoader) {
        try {
            val appStandbyControllerClass =
                classLoader.loadClass("com.miui.powerkeeper.controller.AppStandbyController")
            try {
                val setUidStateMethod = appStandbyControllerClass.getDeclaredMethod(
                    "setUidState",
                    Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType
                )
                hookE(setUidStateMethod).intercept { chain: XposedInterface.Chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size > 1 && args[0] is Int) {
                        val uid = args[0] as Int
                        if (isGmsUid(uid) && java.lang.Boolean.TRUE != args[1]) {
                            args[1] = true
                            log(
                                Log.INFO, TAG,
                                "AppStandbyController#setUidState: kept GMS (uid $uid) allowed"
                            )
                        }
                    }
                    chain.proceed(args)
                }
                deoptimize(setUidStateMethod)
                // Tag the log with pkg/userId: this line repeats once per
                // package-ready pass (every hot reload re-runs it), so a raw
                // count reads like several hooks when setId() has in fact
                // collapsed them into a single live one.
                log(
                    Log.INFO, TAG,
                    "AppStandbyController#setUidState hooked for GMS allow re-assert" +
                        " (pkg=$packageName, userId=${Process.myUid() / 100000})"
                )
            } catch (e: NoSuchMethodException) {
                logSkip("AppStandbyController#setUidState absent, skip")
            }
        } catch (e: ClassNotFoundException) {
            logSkip("AppStandbyController class absent, skip")
        }
    }

    private fun hookGlobalFeatureConfigureHelper(classLoader: ClassLoader) {
        try {
            val GlobalFeatureConfigureHelperClass = classLoader.loadClass(
                "com.miui.powerkeeper.provider.GlobalFeatureConfigureHelper"
            )
            for (argType in arrayOf(Bundle::class.java, Context::class.java)) {
                try {
                    val getDozeWhiteListAppsMethod =
                        GlobalFeatureConfigureHelperClass.getDeclaredMethod(
                            "getDozeWhiteListApps", argType
                        )
                    hookE(getDozeWhiteListAppsMethod).intercept { chain: XposedInterface.Chain ->
                        val result = chain.proceed()
                        try {
                            if (result is List<*> && !result.contains(GMS_PACKAGE_NAME)) {
                                val source = result
                                val whiteList = ArrayList<Any?>(source)
                                whiteList.add(GMS_PACKAGE_NAME)
                                addIfAbsentInPlace(source, GMS_PACKAGE_NAME)
                                return@intercept whiteList
                            }
                        } catch (t: Throwable) {
                            log(Log.ERROR, TAG, "Failed to extend doze white list", t)
                        }
                        result
                    }
                } catch (e: NoSuchMethodException) {
                    logSkip(
                        "GlobalFeatureConfigureHelper#getDozeWhiteListApps(" +
                            argType.simpleName + ") absent, skip"
                    )
                }
            }
        } catch (e: ClassNotFoundException) {
            log(Log.ERROR, TAG, "Failed to hook GlobalFeatureConfigureHelper", e)
        }
    }

    /**
     * P1: keep GMS in Settings.System.MILLET_NO_RESTRICT_APP.
     *
     * PowerKeeper generates that setting from userTable rows whose literal
     * bgControl equals "noRestrict". GMS is stuck at "miuiAuto" (scenario 0)
     * because the policy UI hides the selector for packages without a launcher
     * icon, so dealNoRestrictApp() never includes it. Greezer's
     * mNoRestrictAppSet is the shared filter for both the Aurogon quick-freeze
     * path and PowerStrategyMode (tobg / from system); without GMS in the set
     * the UID gets frozen even when mGmsLimitEnabled is false.
     *
     * Hooking inside PowerKeeper removes the race that Shizuku watchdogs have:
     * every regeneration of the projection includes GMS at the source.
     */
    private fun hookNoRestrictList(classLoader: ClassLoader) {
        // Source-level: ensure getNoRestrictApps() always returns GMS.
        try {
            val userConfigureHelperClass =
                classLoader.loadClass("com.miui.powerkeeper.provider.UserConfigureHelper")
            val getNoRestrictAppsMethod = userConfigureHelperClass.getDeclaredMethod(
                "getNoRestrictApps", Context::class.java
            )
            hookE(getNoRestrictAppsMethod).intercept { chain: XposedInterface.Chain ->
                val result = chain.proceed()
                try {
                    if (result is MutableList<*>) {
                        @Suppress("UNCHECKED_CAST")
                        addIfAbsent(result as MutableList<Any?>, GMS_PACKAGE_NAME)
                    } else if (result is List<*>) {
                        val copy = ArrayList<Any?>(result)
                        addIfAbsent(copy, GMS_PACKAGE_NAME)
                        return@intercept copy
                    }
                } catch (t: Throwable) {
                    log(Log.ERROR, TAG, "Failed to extend getNoRestrictApps", t)
                }
                try {
                    ensureGmsUserTableBgControl()
                } catch (t: Throwable) {
                    log(Log.WARN, TAG, "Failed to write back userTable.bgControl", t)
                }
                result
            }
            deoptimize(getNoRestrictAppsMethod)
        } catch (e: NoSuchMethodException) {
            logSkip("UserConfigureHelper#getNoRestrictApps absent, skip")
        } catch (e: ClassNotFoundException) {
            logSkip("UserConfigureHelper class absent, skip")
        }

        // Any user-config writer can put GMS back to miuiAuto; force and re-assert.
        try {
            val writerClass =
                classLoader.loadClass("com.miui.powerkeeper.provider.UserConfigureHelper")
            for (method in writerClass.declaredMethods) {
                val name = method.name
                val looksWriter =
                    name.contains("update", ignoreCase = true) ||
                        name.contains("save", ignoreCase = true) ||
                        name.contains("insert", ignoreCase = true) ||
                        name.contains("modify", ignoreCase = true) ||
                        name.contains("setBg", ignoreCase = true)
                if (!looksWriter || name.contains("get", ignoreCase = true)) continue
                val isBgControlSetter = name.contains("setBgControl", ignoreCase = true)
                hookE(method).intercept { chain: XposedInterface.Chain ->
                    val rawArgs = chain.args
                    var args: Array<Any?>? = null
                    if (isBgControlSetter) {
                        val copy = rawArgs.toTypedArray()
                        val touchesGms = copy.any { it == GMS_PACKAGE_NAME }
                        if (touchesGms) {
                            for (i in copy.indices) {
                                val a = copy[i]
                                if (a is String && a != GMS_PACKAGE_NAME && a != COL_BG_CONTROL) {
                                    copy[i] = BG_CONTROL_NO_RESTRICT
                                    log(
                                        Log.INFO, TAG,
                                        "setBgControl: forced GMS control $a -> $BG_CONTROL_NO_RESTRICT"
                                    )
                                }
                            }
                            args = copy
                        }
                    }
                    val result = if (args != null) chain.proceed(args) else chain.proceed()
                    try {
                        ensureGmsUserTableBgControl()
                    } catch (t: Throwable) {
                        log(Log.WARN, TAG, "userTable re-assert after ${method.name} failed", t)
                    }
                    result
                }
                deoptimize(method)
                log(Log.INFO, TAG, "UserConfigureHelper#${method.name} hooked for userTable re-assert")
            }
        } catch (e: ClassNotFoundException) {
            // Already reported above when getNoRestrictApps was missing.
        }

        // Belt-and-suspenders: after dealNoRestrictApp() writes the projection,
        // verify GMS is present and repair if a path bypassed getNoRestrictApps.
        try {
            val activeStateControllerClass =
                classLoader.loadClass("com.miui.powerkeeper.controller.ActiveStateController")
            val dealNoRestrictAppMethod = activeStateControllerClass.getDeclaredMethod(
                "dealNoRestrictApp"
            )
            hookE(dealNoRestrictAppMethod).intercept { chain: XposedInterface.Chain ->
                chain.proceed()
                try {
                    ensureGmsInMilletSetting()
                } catch (t: Throwable) {
                    log(Log.ERROR, TAG, "Failed to repair MILLET_NO_RESTRICT_APP", t)
                }
            }
            deoptimize(dealNoRestrictAppMethod)
        } catch (e: NoSuchMethodException) {
            logSkip("ActiveStateController#dealNoRestrictApp absent, skip")
        } catch (e: ClassNotFoundException) {
            logSkip("ActiveStateController class absent, skip")
        }
    }

    /**
     * Read Settings.System.MILLET_NO_RESTRICT_APP and append GMS when missing.
     * Preserves every existing entry and ordering. Also writes GMS's
     * userTable.bgControl back to "noRestrict" so the source row matches.
     * After a repair, triggers P4 recovery so an already-frozen GMS gets a
     * chance to reconnect.
     */
    private fun ensureGmsInMilletSetting() {
        ensureGmsUserTableBgControl()
        val context = getSystemContext() ?: getPowerKeeperContext() ?: return
        val resolver = context.contentResolver
        val raw = android.provider.Settings.System.getString(resolver, MILLET_NO_RESTRICT_APP_KEY)
            ?: ""
        val entries = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (entries.contains(GMS_PACKAGE_NAME)) return
        val updated = if (entries.isEmpty()) {
            GMS_PACKAGE_NAME
        } else {
            entries.joinToString(", ") + ", " + GMS_PACKAGE_NAME
        }
        android.provider.Settings.System.putString(resolver, MILLET_NO_RESTRICT_APP_KEY, updated)
        log(Log.INFO, TAG, "MILLET_NO_RESTRICT_APP: appended GMS (was: $raw)")
        // P4: if GMS was frozen during the missing-entry window, nudge it awake.
        recoverGmsConnection(context)
    }

    /**
     * Write-back: set GMS's PowerKeeper userTable.bgControl to "noRestrict".
     *
     * P1 already injects GMS into the no-restrict projection and P3 rewrites
     * the compiled scenario; this keeps the *source* row aligned so
     * dealNoRestrictApp() and any regeneration that literally filters
     * bgControl="noRestrict" include GMS without leaning on the interceptors.
     * Only the GMS row is touched; other packages keep whatever the user set.
     */
    private fun ensureGmsUserTableBgControl() {
        if (userTableReassertInFlight) return
        userTableReassertInFlight = true
        try {
            val pk = getPowerKeeperContext()
            val sys = getSystemContext()
            val context = pk ?: sys
            if (context == null) {
                log(Log.WARN, TAG, "userTable: no Context (powerKeeper=$pk system=$sys), skip write-back")
                return
            }
            log(Log.INFO, TAG, "userTable: ensure GMS bgControl via ${if (pk != null) "powerkeeper" else "system"}")
            val uri = android.net.Uri.parse(USER_TABLE_URI)
            var current: String? = null
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(COL_BG_CONTROL),
                    "$COL_PKG_NAME = ?",
                    arrayOf(GMS_PACKAGE_NAME),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        current = cursor.getString(0)
                    }
                }
            } catch (t: Throwable) {
                log(Log.WARN, TAG, "userTable: query failed", t)
                return
            }
            log(Log.INFO, TAG, "userTable: GMS current bgControl=$current")
            if (current == BG_CONTROL_NO_RESTRICT) return

            val values = android.content.ContentValues()
            values.put(COL_BG_CONTROL, BG_CONTROL_NO_RESTRICT)
            try {
                val updated = context.contentResolver.update(
                    uri,
                    values,
                    "$COL_PKG_NAME = ?",
                    arrayOf(GMS_PACKAGE_NAME)
                )
                log(Log.INFO, TAG, "userTable: update $current -> $BG_CONTROL_NO_RESTRICT count=$updated")
                if (updated > 0) return
            } catch (t: Throwable) {
                log(Log.WARN, TAG, "userTable: update failed", t)
            }
            values.put(COL_PKG_NAME, GMS_PACKAGE_NAME)
            values.put(COL_USER_ID, 0)
            values.put(COL_LAST_CONFIGURED, System.currentTimeMillis())
            try {
                val inserted = context.contentResolver.insert(uri, values)
                log(Log.INFO, TAG, "userTable: insert result=$inserted")
            } catch (t: Throwable) {
                log(Log.WARN, TAG, "userTable: insert GMS row failed", t)
            }
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "userTable: ensure failed", t)
        } finally {
            userTableReassertInFlight = false
        }
    }

    /**
     * P4: ask GMS/GSF to re-establish its FCM connection and un-freeze.
     *
     * All actions are outbound IPC TO GMS/GSF (broadcasts + content query),
     * not hooks inside GMS. Inspired by FCMGuard's heartbeat approach:
     * GCM_RECONNECT alone may miss the MCS/GTalk reconnect paths on some
     * builds, so GTALK_HEARTBEAT and MCS_HEARTBEAT are also sent. GSF
     * (com.google.android.gsf) participates in the FCM transport chain
     * alongside GMS and is included as a target.
     */
    private fun recoverGmsConnection(context: Context) {
        for (target in arrayOf(GMS_PACKAGE_NAME, GSF_PACKAGE_NAME)) {
            for (action in RECOVERY_BROADCAST_ACTIONS) {
                try {
                    val intent = Intent(action)
                    intent.setPackage(target)
                    context.sendBroadcast(intent)
                } catch (t: Throwable) {
                    log(Log.WARN, TAG, "Failed to send $action to $target", t)
                }
            }
        }
        log(Log.INFO, TAG, "P4: recovery broadcasts sent to GMS+GSF")
        try {
            val uri = android.net.Uri.parse(CHIMERA_PROVIDER_URI)
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.close()
            log(Log.INFO, TAG, "Chimera provider query completed")
        } catch (t: Throwable) {
            log(Log.WARN, TAG, "Chimera provider query failed (non-fatal)", t)
        }
    }

    /**
     * PowerKeeper's own Context, distinct from system_server's.
     * Cached after the first successful lookup in this process.
     */
    @Volatile
    private var powerKeeperContext: Context? = null

    private fun getPowerKeeperContext(): Context? {
        if (powerKeeperContext != null) return powerKeeperContext
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApplication = activityThreadClass.getMethod("currentApplication")
            val ctx = currentApplication.invoke(null)
            if (ctx is Context) {
                powerKeeperContext = ctx
                ctx
            } else {
                null
            }
        } catch (ignored: Throwable) {
            null
        }
    }

    /**
     * P3: force GMS's compiled scenario to 8 (noRestrict) instead of 0.
     *
     * fillScenarioContent() special-cases GmsCoreUtils.isGmsCoreApp: a
     * miuiAuto row becomes scenario 0 for GMS but scenario 2 for normal apps.
     * Scenario 0 makes isNoRestrict() return true (so older code thinks GMS is
     * unrestricted) yet dealNoRestrictApp() only queries the literal
     * bgControl="noRestrict" rows — so GMS never enters MILLET_NO_RESTRICT_APP.
     *
     * Rewriting scenario 0 → 8 for GMS makes the compiled profile match the
     * "noRestrict" scenario that a normal app gets when the user selects
     * "Unrestricted", aligning UI / AOSP DeviceIdle / private policy state.
     *
     * Field layout (verified from OS3/OS4 PowerKeeper DEX):
     *   PowerKeeperAppConfigure.pkg : String
     *   PowerKeeperAppConfigure.scenario : int
     *
     * OS3 fillScenarioContent(Context,int,PowerKeeperAppConfigure,
     *   UserConfigureHelper,String,List,List)V
     * OS4 adds a trailing Map parameter.
     */
    private fun hookScenarioCompiler(classLoader: ClassLoader) {
        val configureClass =
            classLoader.loadClass("com.miui.powerkeeper.provider.PowerKeeperAppConfigure")
        val pkgField = configureClass.getDeclaredField("pkg")
        pkgField.isAccessible = true
        val scenarioField = configureClass.getDeclaredField("scenario")
        scenarioField.isAccessible = true

        // OS3: 7 params; OS4: 8 params (extra Map). PowerKeeperAppConfigure is arg 2.
        for (paramCount in intArrayOf(7, 8)) {
            val method = configureClass.declaredMethods.firstOrNull { m ->
                m.name == "fillScenarioContent" && m.parameterCount == paramCount
            } ?: continue

            hookE(method).intercept { chain: XposedInterface.Chain ->
                chain.proceed()
                try {
                    val cfg = chain.getArg(2) ?: return@intercept null
                    val pkg = pkgField.get(cfg) as? String ?: return@intercept null
                    if (GMS_PACKAGE_NAME != pkg) return@intercept null
                    val scenario = scenarioField.getInt(cfg)
                    if (scenario == SCENARIO_MUI_AUTO_GMS) {
                        scenarioField.setInt(cfg, SCENARIO_NO_RESTRICT)
                        log(
                            Log.INFO, TAG,
                            "P3: rewrote GMS scenario $SCENARIO_MUI_AUTO_GMS → $SCENARIO_NO_RESTRICT"
                        )
                    }
                    try {
                        ensureGmsUserTableBgControl()
                    } catch (t: Throwable) {
                        log(Log.WARN, TAG, "P3: userTable write-back failed", t)
                    }
                } catch (t: Throwable) {
                    log(Log.ERROR, TAG, "P3: failed to rewrite GMS scenario", t)
                }
                null
            }
            deoptimize(method)
            log(Log.INFO, TAG, "PowerKeeperAppConfigure#fillScenarioContent($paramCount args) hooked for P3")
        }
    }

    /**
     * P2: Greezer freeze-path safety net in system_server.
     *
     * Verified against OS3/OS4 miui-services.jar:
     * - AurogonImmobulusMode.isNoRestrictApp(String)Z  — the exact mNoRestrictAppSet
     *   check used by both lambda$triggerQuickFreeze$0 and PolicyMaker's filter chain.
     * - AurogonImmobulusMode.triggerQuickFreeze(I,I)V
     * - PolicyMaker.isAllowFreeze(I)I  — returns int (CANNOT_FREEZE constant).
     * - OS4 extra: isNoRestrictFreezeable(String,I)Z.
     *
     * All targets live in miui-services.jar (system_server). GMS itself is never
     * hooked — only the framework-side freeze policy is told to treat GMS as
     * no-restrict.
     */
    private fun hookGreezerNoRestrict(classLoader: ClassLoader) {
        // Primary: isNoRestrictApp(pkg) — boolean, no constant guessing needed.
        // Returning true means "GMS is in the no-restrict set", so every freeze
        // path that consults mNoRestrictAppSet (Aurogon quick-freeze and
        // PowerStrategyMode) skips GMS.
        try {
            val aurogonClass =
                classLoader.loadClass("com.miui.server.greeze.AurogonImmobulusMode")
            try {
                val isNoRestrictAppMethod = aurogonClass.getDeclaredMethod(
                    "isNoRestrictApp", String::class.java
                )
                hookE(isNoRestrictAppMethod).intercept { chain: XposedInterface.Chain ->
                    val pkg = chain.getArg(0)
                    if (GMS_PACKAGE_NAME == pkg) {
                        return@intercept true
                    }
                    chain.proceed()
                }
                deoptimize(isNoRestrictAppMethod)
            } catch (e: NoSuchMethodException) {
                logSkip("AurogonImmobulusMode#isNoRestrictApp absent, skip")
            }

            // OS4: isNoRestrictFreezeable(pkg, reason) — false means "do not freeze".
            try {
                val isNoRestrictFreezeableMethod = aurogonClass.getDeclaredMethod(
                    "isNoRestrictFreezeable", String::class.java, Int::class.javaPrimitiveType
                )
                hookE(isNoRestrictFreezeableMethod).intercept { chain: XposedInterface.Chain ->
                    val pkg = chain.getArg(0)
                    if (GMS_PACKAGE_NAME == pkg) {
                        return@intercept false
                    }
                    chain.proceed()
                }
                deoptimize(isNoRestrictFreezeableMethod)
            } catch (e: NoSuchMethodException) {
                logSkipOtherGeneration("AurogonImmobulusMode#isNoRestrictFreezeable absent, skip")
            }

            // triggerQuickFreeze(uid, reason) — skip GMS entirely.
            val triggerQuickFreezeMethods = aurogonClass.declaredMethods.filter { m ->
                m.name == "triggerQuickFreeze" && m.parameterCount == 2
            }
            if (triggerQuickFreezeMethods.isEmpty()) {
                logSkip("AurogonImmobulusMode#triggerQuickFreeze(I,I) absent, skip")
            } else {
                for (method in triggerQuickFreezeMethods) {
                    method.isAccessible = true
                    hookE(method).intercept { chain: XposedInterface.Chain ->
                        val uid = chain.getArg(0)
                        if (uid is Int && isGmsUid(uid)) {
                            return@intercept skipValueFor(method.returnType)
                        }
                        chain.proceed()
                    }
                    deoptimize(method)
                }
                log(Log.INFO, TAG, "AurogonImmobulusMode#triggerQuickFreeze hooked")
            }
        } catch (e: ClassNotFoundException) {
            logSkip("AurogonImmobulusMode class absent, skip")
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook AurogonImmobulusMode", e)
        }

        // PolicyMaker.isAllowFreeze(uid): int return (CANNOT_FREEZE when in the
        // no-restrict set). Returning 0 / false for GMS closes the PowerStrategyMode
        // (tobg / from system) window before PowerKeeper regenerates the projection.
        try {
            val policyMakerClass =
                classLoader.loadClass("com.miui.server.greeze.power.PolicyMaker")
            val isAllowFreezeMethods = policyMakerClass.declaredMethods.filter { m ->
                m.name == "isAllowFreeze" && m.parameterCount == 1
            }
            if (isAllowFreezeMethods.isEmpty()) {
                logSkip("PolicyMaker#isAllowFreeze absent, skip")
            } else {
                for (method in isAllowFreezeMethods) {
                    method.isAccessible = true
                    hookE(method).intercept { chain: XposedInterface.Chain ->
                        val uid = chain.getArg(0)
                        if (uid is Int && isGmsUid(uid)) {
                            return@intercept skipValueFor(method.returnType)
                        }
                        chain.proceed()
                    }
                    deoptimize(method)
                    log(Log.INFO, TAG, "PolicyMaker#isAllowFreeze hooked (${method.returnType.simpleName})")
                }
            }
        } catch (e: ClassNotFoundException) {
            logSkip("PolicyMaker class absent, skip")
        } catch (e: Throwable) {
            log(Log.ERROR, TAG, "Failed to hook PolicyMaker#isAllowFreeze", e)
        }
    }

    /**
     * True when [uid] belongs to GMS (any Android user). Uses the package
     * manager when available; falls back to comparing against known GMS UIDs
     * resolved once per process.
     */
    private fun isGmsUid(uid: Int): Boolean {
        val appId = uid % 100000
        // Cache hit: done.
        if (cachedGmsAppId != null) {
            return appId == cachedGmsAppId
        }
        // First call: resolve and cache.
        val gms = gmsUid()
        if (gms != null) {
            cachedGmsAppId = gms % 100000
            return appId == cachedGmsAppId
        }
        // gmsUid() failed; try PackageManager directly.
        return try {
            val context = getSystemContext() ?: return false
            val info = context.packageManager.getApplicationInfo(GMS_PACKAGE_NAME, 0)
            val resolved = info.uid % 100000
            cachedGmsAppId = resolved
            appId == resolved
        } catch (ignored: Throwable) {
            false
        }
    }

    @Volatile
    private var cachedGmsAppId: Int? = null

    @Volatile
    private var sAllowlist: Set<String> = emptySet()

    @Volatile
    private var sStrictMode = false

    private fun loadAllowlistFromRemotePrefs() {
        try {
            val prefs = getRemotePreferences(Prefs.GROUP_CONFIG)
            val set = prefs.getStringSet(Prefs.KEY_ALLOWLIST, emptySet())
            sAllowlist = if (set != null) HashSet(set) else HashSet()
            sStrictMode = prefs.getBoolean(Prefs.KEY_STRICT_MODE, false)
        } catch (e: Exception) {
            log(Log.ERROR, TAG, "Failed to read remote allowlist", e)
        }
        sAllowlistReadMs = SystemClock.uptimeMillis()
    }

    private fun hookAllowlist() {
        loadAllowlistFromRemotePrefs()
        installAllowlistReceiverAsync()
    }

    @Volatile
    private var allowlistReceiverRegistered = false
    private val allowlistRegistering = AtomicBoolean(false)
    private val allowlistReloadQueued = AtomicBoolean(false)

    @Volatile
    private var sAllowlistReadMs = 0L

    @Volatile
    private var allowlistHandler: Handler? = null

    private fun allowlistBackgroundHandler(): Handler {
        val handler = allowlistHandler
        if (handler != null) {
            return handler
        }
        synchronized(this) {
            if (allowlistHandler == null) {
                val thread = HandlerThread("fcmlive-allowlist")
                thread.start()
                allowlistHandler = Handler(thread.looper)
            }
            return allowlistHandler!!
        }
    }

    private fun requestAllowlistReload() {
        val sinceLastRead = SystemClock.uptimeMillis() - sAllowlistReadMs
        if (sinceLastRead >= ALLOWLIST_RELOAD_MIN_MS) {
            allowlistBackgroundHandler().post { loadAllowlistFromRemotePrefs() }
            return
        }
        if (!allowlistReloadQueued.compareAndSet(false, true)) {
            return
        }
        allowlistBackgroundHandler().postDelayed({
            allowlistReloadQueued.set(false)
            loadAllowlistFromRemotePrefs()
        }, ALLOWLIST_RELOAD_MIN_MS - sinceLastRead)
    }

    private fun installAllowlistReceiverAsync() {
        if (allowlistReceiverRegistered) {
            return
        }
        val t = Thread({
            for (attempt in 0 until ALLOWLIST_REGISTER_MAX_ATTEMPTS) {
                if (registerAllowlistReceiver()) {
                    return@Thread
                }
                try {
                    Thread.sleep(ALLOWLIST_REGISTER_RETRY_MS)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
            log(
                Log.WARN, TAG, "Allowlist receiver not installed during boot;" +
                    " falling back to lazy registration"
            )
        }, "fcmlive-allowlist-register")
        t.isDaemon = true
        t.start()
    }

    private fun getFcmAllowlist(): Set<String> {
        registerAllowlistReceiver()
        if (!allowlistReceiverRegistered &&
            SystemClock.uptimeMillis() - sAllowlistReadMs >= ALLOWLIST_STALE_MS
        ) {
            requestAllowlistReload()
        }
        return HashSet(sAllowlist)
    }

    private fun shouldWake(targetPackage: String?): Boolean {
        val allowlist = getFcmAllowlist()
        return allowlist.isEmpty() || allowlist.contains(targetPackage)
    }

    private fun shouldApply(packageName: String?): Boolean {
        if (!sStrictMode) {
            return true
        }
        val allowlist = getFcmAllowlist()
        if (allowlist.isEmpty()) {
            return true
        }
        return allowlist.contains(packageName) ||
            GMS_PACKAGE_NAME == packageName ||
            GMS_PERSISTENT_PROCESS_NAME == packageName
    }

    private fun registerAllowlistReceiver(): Boolean {
        if (allowlistReceiverRegistered) {
            return true
        }
        if (!allowlistRegistering.compareAndSet(false, true)) {
            return allowlistReceiverRegistered
        }
        try {
            val sys = getSystemContext() ?: return false
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (Prefs.ACTION_ALLOWLIST_CHANGED == intent.action) {
                        requestAllowlistReload()
                    }
                }
            }
            val filter = IntentFilter(Prefs.ACTION_ALLOWLIST_CHANGED)
            val handler = allowlistBackgroundHandler()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                sys.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_EXPORTED)
            } else {
                sys.registerReceiver(receiver, filter, null, handler)
            }
            allowlistReceiverRegistered = true
            log(Log.INFO, TAG, "Allowlist receiver installed")
            return true
        } catch (e: Throwable) {
            return false
        } finally {
            allowlistRegistering.set(false)
        }
    }

    private fun findMethod(
        owner: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>?
    ): Method? {
        return try {
            owner.getDeclaredMethod(name, *parameterTypes)
        } catch (e: NoSuchMethodException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private fun callerIsGms(
        getRecordMethod: Method?,
        infoField: Field,
        ams: Any,
        callerThread: Any?
    ): Boolean {
        if (getRecordMethod != null && callerThread != null) {
            try {
                val app = getInvoker(getRecordMethod).invoke(ams, callerThread)
                val info = if (app != null) infoField.get(app) else null
                if (info is ApplicationInfo) {
                    return GMS_PACKAGE_NAME == info.packageName
                }
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "Failed to resolve the broadcast caller", t)
            }
        }
        return binderCallerIsGms()
    }

    private fun binderCallerIsGms(): Boolean {
        try {
            val context = getSystemContext() ?: return false
            val packages = context.packageManager
                .getPackagesForUid(Binder.getCallingUid()) ?: return false
            for (pkg in packages) {
                if (GMS_PACKAGE_NAME == pkg) {
                    return true
                }
            }
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "Failed to resolve the binder caller uid", t)
        }
        return false
    }

    private fun hookActivityManagerService(classLoader: ClassLoader) {
        val ActivityManagerServiceClass =
            classLoader.loadClass("com.android.server.am.ActivityManagerService")
        val mContextField = ActivityManagerServiceClass.getDeclaredField("mContext")
        mContextField.isAccessible = true
        val IApplicationThreadClass = classLoader.loadClass("android.app.IApplicationThread")
        val IIntentReceiverClass = classLoader.loadClass("android.content.IIntentReceiver")
        val ProcessRecordClass = classLoader.loadClass("com.android.server.am.ProcessRecord")
        val infoField = ProcessRecordClass.getDeclaredField("info")
        infoField.isAccessible = true
        var getRecordMethod = findMethod(
            ActivityManagerServiceClass,
            "getRecordForAppLOSP", IApplicationThreadClass
        )
        if (getRecordMethod == null) {
            getRecordMethod = findMethod(
                ActivityManagerServiceClass,
                "getRecordForAppLocked", IApplicationThreadClass
            )
        }
        if (getRecordMethod == null) {
            log(
                Log.WARN, TAG, "No ActivityManagerService#getRecordForApp*;" +
                    " the broadcast caller is identified by binder uid instead"
            )
        }
        var broadcastMethod: Method? = null
        var intentArgIndex = 2
        val featureSignatures = listOf(
            arrayOf<Class<*>>(
                IApplicationThreadClass, String::class.java, Intent::class.java, String::class.java,
                IIntentReceiverClass, Int::class.javaPrimitiveType!!, String::class.java,
                Bundle::class.java,
                Array<String>::class.java, Array<String>::class.java, Array<String>::class.java,
                Int::class.javaPrimitiveType!!, Bundle::class.java,
                Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            ),
            arrayOf<Class<*>>(
                IApplicationThreadClass, String::class.java, Intent::class.java, String::class.java,
                IIntentReceiverClass, Int::class.javaPrimitiveType!!, String::class.java,
                Bundle::class.java,
                Array<String>::class.java, Array<String>::class.java,
                Int::class.javaPrimitiveType!!, Bundle::class.java,
                Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            ),
            arrayOf<Class<*>>(
                IApplicationThreadClass, String::class.java, Intent::class.java, String::class.java,
                IIntentReceiverClass, Int::class.javaPrimitiveType!!, String::class.java,
                Bundle::class.java,
                Array<String>::class.java,
                Int::class.javaPrimitiveType!!, Bundle::class.java,
                Boolean::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            )
        )
        for (signature in featureSignatures) {
            broadcastMethod = findMethod(
                ActivityManagerServiceClass,
                "broadcastIntentWithFeature", *signature
            )
            if (broadcastMethod != null) {
                break
            }
        }
        if (broadcastMethod == null) {
            broadcastMethod = findMethod(
                ActivityManagerServiceClass, "broadcastIntent",
                IApplicationThreadClass,
                Intent::class.java, String::class.java, IIntentReceiverClass,
                Int::class.javaPrimitiveType, String::class.java, Bundle::class.java,
                Array<String>::class.java, Int::class.javaPrimitiveType, Bundle::class.java,
                Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            if (broadcastMethod != null) {
                intentArgIndex = 1
            }
        }
        if (broadcastMethod == null) {
            log(
                Log.ERROR, TAG, "No broadcastIntent* in ActivityManagerService;" +
                    " stopped-package delivery and the power exemption are not installed"
            )
            return
        }
        val finalGetRecordMethod = getRecordMethod
        val finalIntentArgIndex = intentArgIndex
        hookE(broadcastMethod).intercept { chain: XposedInterface.Chain ->
            val intent = chain.getArg(finalIntentArgIndex) as? Intent
            if (intent != null && ACTION_REMOTE_INTENT == intent.action) {
                try {
                    val targetPackage = targetPackageOf(intent)
                    if (callerIsGms(
                            finalGetRecordMethod, infoField,
                            chain.thisObject, chain.getArg(0)
                        ) &&
                        targetPackage != null &&
                        shouldWake(targetPackage)
                    ) {
                        try {
                            if ((intent.flags and Intent.FLAG_INCLUDE_STOPPED_PACKAGES) == 0) {
                                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            }
                        } catch (t: Throwable) {
                            log(Log.ERROR, TAG, "Failed to add FLAG_INCLUDE_STOPPED_PACKAGES", t)
                        }
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val mContext = mContextField.get(chain.thisObject) as? Context
                                if (mContext != null) {
                                    getPowerExemptionManager(mContext).addToTemporaryAllowList(
                                        targetPackage,
                                        102,
                                        "GOOGLE_C2DM",
                                        2000
                                    )
                                }
                            }
                        } catch (t: Throwable) {
                            log(Log.ERROR, TAG, "Failed to add temporary power exemption", t)
                        }
                    }
                } catch (t: Throwable) {
                    log(Log.ERROR, TAG, "C2DM broadcast hook failed", t)
                }
            }
            chain.proceed()
        }
        deoptimize(broadcastMethod)
    }

    private fun skipValueFor(returnType: Class<*>): Any? {
        if (returnType == Void.TYPE || !returnType.isPrimitive) {
            return null
        }
        return when (returnType) {
            Boolean::class.javaPrimitiveType -> java.lang.Boolean.FALSE
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Short::class.javaPrimitiveType -> 0.toShort()
            Byte::class.javaPrimitiveType -> 0.toByte()
            Char::class.javaPrimitiveType -> 0.toChar()
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            else -> null
        }
    }

    @Volatile
    private var restrictNetMatchLogged = false

    /** Guards userTable write-back against re-entry via hooked config writers. */
    @Volatile
    private var userTableReassertInFlight = false

    private fun hookInternationalPolicyManager(classLoader: ClassLoader) {
        val InternationalPolicyManagerClass =
            classLoader.loadClass("com.miui.server.greeze.InternationalPolicyManager")
        val isPushAppMethod = InternationalPolicyManagerClass.getDeclaredMethod(
            "isPushApp", String::class.java
        )
        val restrictNetOwner = InternationalPolicyManagerClass.name
        val systemServerCl = InternationalPolicyManagerClass.classLoader
        hookE(isPushAppMethod).intercept { chain: XposedInterface.Chain ->
            val pkg = chain.getArg(0) as? String
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                shouldApply(pkg)
            ) {
                try {
                    val fromRestrictNet = STACK_WALKER.walk { frames ->
                        frames.anyMatch { frame ->
                            "isRestrictNet" == frame.methodName &&
                                (restrictNetOwner == frame.className ||
                                    (frame.declaringClass != null &&
                                        frame.declaringClass.classLoader === systemServerCl))
                        }
                    }
                    if (fromRestrictNet) {
                        if (!restrictNetMatchLogged) {
                            restrictNetMatchLogged = true
                            log(
                                Log.INFO, TAG, "isPushApp: caller isRestrictNet matched;" +
                                    " answering false"
                            )
                        }
                        return@intercept false
                    }
                } catch (t: Throwable) {
                    log(Log.ERROR, TAG, "Stack inspection failed", t)
                }
            }
            chain.proceed()
        }
    }

    private fun hookProcessCleanerBase(classLoader: ClassLoader) {
        val ProcessCleanerBaseClass =
            classLoader.loadClass("com.android.server.am.ProcessCleanerBase")
        val ProcessRecordClass = classLoader.loadClass("com.android.server.am.ProcessRecord")
        val mGetApplicationInfo = ProcessRecordClass.getDeclaredMethod("getApplicationInfo")
        val ProcessManagerServiceClass =
            classLoader.loadClass("com.android.server.am.ProcessManagerService")
        val mPkms = ProcessManagerServiceClass.getDeclaredField("mPkms")
        mPkms.isAccessible = true
        val isForceStopEnableMethod = ProcessCleanerBaseClass.getDeclaredMethod(
            "isForceStopEnable",
            ProcessRecordClass,
            Int::class.javaPrimitiveType,
            ProcessManagerServiceClass
        )
        hookE(isForceStopEnableMethod).intercept { chain: XposedInterface.Chain ->
            try {
                val policy = chain.getArg(1)
                val pms = chain.getArg(2)
                val pm = if (pms != null) mPkms.get(pms) as? PackageManager else null
                val info =
                    getInvoker(mGetApplicationInfo).invoke(chain.getArg(0)) as? ApplicationInfo
                val pkgName = info?.packageName
                if (policy is Int && policy != 13 &&
                    pm != null &&
                    pkgName != null &&
                    shouldApply(pkgName) &&
                    declaresFcmComponent(pm, pkgName)
                ) {
                    return@intercept false
                }
            } catch (t: Throwable) {
                log(Log.ERROR, TAG, "isForceStopEnable hook failed", t)
            }
            chain.proceed()
        }
    }

    private val fcmCache = HashMap<String, FcmQuery>()

    private fun declaresFcmComponent(pm: PackageManager, packageName: String): Boolean {
        val now = SystemClock.uptimeMillis()
        synchronized(fcmCache) {
            val cached = fcmCache[packageName]
            if (cached != null && now - cached.checkedAtMs < FCM_CACHE_TTL_MS) {
                return cached.declares
            }
        }
        val declares: Boolean = try {
            declaresFcmUncached(pm, packageName)
        } catch (t: Throwable) {
            log(Log.ERROR, TAG, "FCM lookup failed for $packageName", t)
            return false
        }
        synchronized(fcmCache) {
            if (fcmCache.size >= FCM_CACHE_MAX) {
                fcmCache.clear()
            }
            fcmCache[packageName] = FcmQuery(declares, now)
        }
        return declares
    }

    private fun declaresFcmUncached(pm: PackageManager, packageName: String): Boolean {
        val serviceIntent = Intent(ACTION_MESSAGING_EVENT)
        serviceIntent.setPackage(packageName)
        if (pm.queryIntentServices(serviceIntent, 0).isNotEmpty()) {
            return true
        }
        val receiverIntent = Intent(ACTION_REMOTE_INTENT)
        receiverIntent.setPackage(packageName)
        if (pm.queryBroadcastReceivers(receiverIntent, 0).isNotEmpty()) {
            return true
        }
        try {
            pm.getServiceInfo(ComponentName(packageName, FCM_MESSAGING_SERVICE_CLASS), 0)
            return true
        } catch (ignored: Throwable) {
        }
        try {
            pm.getReceiverInfo(ComponentName(packageName, FCM_IID_RECEIVER_CLASS), 0)
            return true
        } catch (ignored: Throwable) {
        }
        return false
    }

    private class FcmQuery(val declares: Boolean, val checkedAtMs: Long)

    companion object {
        private const val TAG = "HyperGreeze"
        private val CN_DEFER_BROADCAST = listOf(
            "com.google.android.intent.action.GCM_RECONNECT",
            "com.google.android.gcm.DISCONNECTED",
            "com.google.android.gcm.CONNECTED",
            "com.google.android.gms.gcm.HEARTBEAT_ALARM"
        )

        const val ACTION_REMOTE_INTENT = "com.google.android.c2dm.intent.RECEIVE"
        const val ACTION_MESSAGING_EVENT = "com.google.firebase.MESSAGING_EVENT"
        const val FCM_MESSAGING_SERVICE_CLASS =
            "com.google.firebase.messaging.FirebaseMessagingService"
        const val FCM_IID_RECEIVER_CLASS =
            "com.google.firebase.iid.FirebaseInstanceIdReceiver"
        private const val GMS_PACKAGE_NAME = "com.google.android.gms"
        private const val GMS_PERSISTENT_PROCESS_NAME = "com.google.android.gms.persistent"
        private const val MILLET_NO_RESTRICT_APP_KEY = "MILLET_NO_RESTRICT_APP"

        /** PowerKeeper user config table: source row for bgControl. */
        private const val USER_TABLE_URI = "content://com.miui.powerkeeper.configure/userTable"
        private const val COL_PKG_NAME = "pkgName"
        private const val COL_USER_ID = "userId"
        private const val COL_LAST_CONFIGURED = "lastConfigured"
        private const val COL_BG_CONTROL = "bgControl"
        private const val BG_CONTROL_NO_RESTRICT = "noRestrict"

        /** P3 scenario constants (from live PowerKeeper dumps). */
        private const val SCENARIO_MUI_AUTO_GMS = 0   // isGmsCoreApp + miuiAuto
        private const val SCENARIO_NO_RESTRICT = 8    // bgControl = noRestrict

        /** P4 recovery actions (outbound IPC to GMS/GSF, not hooks). */
        private const val ACTION_GCM_RECONNECT = "com.google.android.intent.action.GCM_RECONNECT"
        private const val ACTION_GTALK_HEARTBEAT = "com.google.android.intent.action.GTALK_HEARTBEAT"
        private const val ACTION_MCS_HEARTBEAT = "com.google.android.intent.action.MCS_HEARTBEAT"
        private const val GSF_PACKAGE_NAME = "com.google.android.gsf"
        private val RECOVERY_BROADCAST_ACTIONS = arrayOf(
            ACTION_GCM_RECONNECT,
            ACTION_GTALK_HEARTBEAT,
            ACTION_MCS_HEARTBEAT
        )
        private const val CHIMERA_PROVIDER_URI = "content://com.google.android.gms.chimera"

        private const val ALLOWLIST_STALE_MS = 10_000L
        private const val ALLOWLIST_RELOAD_MIN_MS = 500L
        private const val ALLOWLIST_REGISTER_RETRY_MS = 1_000L
        private const val ALLOWLIST_REGISTER_MAX_ATTEMPTS = 120
        private const val FCM_CACHE_TTL_MS = 5L * 60L * 1000L
        private const val FCM_CACHE_MAX = 256

        private val STACK_WALKER: StackWalker =
            StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)

        private var powerExemptionManager: PowerExemptionManager? = null

        @RequiresApi(Build.VERSION_CODES.S)
        private fun getPowerExemptionManager(context: Context): PowerExemptionManager {
            if (powerExemptionManager == null) {
                powerExemptionManager = PowerExemptionManager(context)
            }
            return powerExemptionManager!!
        }

        private fun targetPackageOf(intent: Intent): String? {
            val pkg = intent.getPackage()
            if (pkg != null) {
                return pkg
            }
            val component = intent.component
            return component?.packageName
        }
    }
}
