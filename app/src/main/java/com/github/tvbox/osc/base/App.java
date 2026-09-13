package com.github.tvbox.osc.base;

import android.app.Activity;
import android.util.Log;
import androidx.multidex.MultiDexApplication;

import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.callback.EmptyCallback;
import com.github.tvbox.osc.callback.LoadingCallback;
import com.github.tvbox.osc.data.AppDataManager;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.EpgUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.js.JSEngine;
import com.kingja.loadsir.core.LoadSir;
import com.orhanobut.hawk.Hawk;

import org.conscrypt.Conscrypt;

import java.security.Security;

import me.jessyan.autosize.AutoSizeConfig;
import me.jessyan.autosize.unit.Subunits;

/**
 * @author pj567
 * @date :2020/12/17
 * @description:
 */
public class App extends MultiDexApplication {
    private static final String LEGACY_DEFAULT_CONFIG_URL =
            "https://raw.githubusercontent.com/xingkoo/sharp-tvbox-legacy/3abfebb/configs/sharp-tvbox-android44-candidates.json";
    private static final String LEGACY_DOH_MIGRATION = "sharp44_doh_disabled_v1";
    private static App instance;

    @Override
    public void onCreate() {
        super.onCreate();
        installModernTlsProvider();
        instance = this;
        initParams();
        // OKGo
        OkGoHelper.init(); //台标获取
        EpgUtil.init();
        // 初始化Web服务器
        ControlManager.init(this);
        //初始化数据库
        AppDataManager.init();
        LoadSir.beginBuilder()
                .addCallback(new EmptyCallback())
                .addCallback(new LoadingCallback())
                .commit();
        AutoSizeConfig.getInstance().setCustomFragment(true).getUnitsManager()
                .setSupportDP(false)
                .setSupportSP(false)
                .setSupportSubunits(Subunits.MM);
        PlayerHelper.init();
        JSEngine.getInstance().create();
    }

    private void installModernTlsProvider() {
        try {
            if (Security.getProvider("Conscrypt") == null) {
                Security.insertProviderAt(Conscrypt.newProvider(), 1);
            }
            Conscrypt.setUseEngineSocketByDefault(false);
            Log.i("TVBox", "TLS provider: " + Security.getProviders()[0].getName());
        } catch (Throwable th) {
            Log.w("TVBox", "Modern TLS provider unavailable", th);
        }
    }

    private void initParams() {
        // Hawk
        Hawk.init(this).build();
        if (!Hawk.contains(HawkConfig.DEBUG_OPEN)) {
            Hawk.put(HawkConfig.DEBUG_OPEN, false);
        }
        if (!Hawk.contains(HawkConfig.PLAY_TYPE)) {
            Hawk.put(HawkConfig.PLAY_TYPE, 1);
        }
        // Android 4.4 cannot negotiate TLS with the public DoH endpoints used
        // here. When DoH is enabled, every hostname lookup fails before even a
        // plain-HTTP source request can start. Migrate existing installs once;
        // this changes only TVBox, never Android's system DNS configuration.
        if (!Hawk.contains(LEGACY_DOH_MIGRATION)) {
            Hawk.put(HawkConfig.DOH_URL, 0);
            Hawk.put(LEGACY_DOH_MIGRATION, true);
        }
        if (!Hawk.contains(HawkConfig.API_URL)) {
            Hawk.put(HawkConfig.API_URL, LEGACY_DEFAULT_CONFIG_URL);
        }
        if (!Hawk.contains(HawkConfig.HOME_REC)) {
            Hawk.put(HawkConfig.HOME_REC, 1);
        }
    }

    public static App getInstance() {
        return instance;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        JSEngine.getInstance().destroy();
    }


    private VodInfo vodInfo;
    public void setVodInfo(VodInfo vodinfo){
        this.vodInfo = vodinfo;
    }
    public VodInfo getVodInfo(){
        return this.vodInfo;
    }

    public Activity getCurrentActivity() {
        return AppManager.getInstance().currentActivity();
    }
}
