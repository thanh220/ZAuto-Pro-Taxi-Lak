package org.zauto;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.view.View;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import android.speech.tts.TextToSpeech;
import java.util.Locale;

public class ZaloWebManager {

    public static WebView hiddenWebView;
    public static FrameLayout webLayout;
    private static final String TAG = "ZAutoWebManager";

    private static Handler watchdogHandler;
    private static Runnable watchdogRunnable;
    public static long lastHeartbeat = System.currentTimeMillis();

    private static long lastReloadTime = 0;
    private static int reloadCountWindow = 0;
    private static long firstReloadInWindow = 0;

    private static WeakReference<Activity> activityRef;

    private static final ConcurrentLinkedQueue<Runnable> replyQueue = new ConcurrentLinkedQueue<>();
    private static boolean isSending = false;

    // =========================================================
    // ỐNG DẪN RAM SIÊU TỐC (BYPASS ANDROID 14 BROADCAST BAN)
    // =========================================================
    public static final ConcurrentLinkedQueue<String> pythonMsgQueue = new ConcurrentLinkedQueue<>();
    public static TextToSpeech tts;
	// =========================================================
    // HÀM ÉP TỈNH NGỦ TỪ PYTHON GỌI XUỐNG
    // =========================================================
    public static void forceWakeup() {
        safeEvaluateJs(
            "try { " +
            "   if(window.zauto_sidebar_observer) { document.querySelectorAll('.msg-item').forEach(scanConvItem); } " +
            "   document.body.dispatchEvent(new MouseEvent('mousemove', {bubbles:true, clientX: 100, clientY: 100}));" +
            "} catch(e) {}"
        );
    }
    // =========================================================
    // REPLY QUEUE
    // =========================================================
    private static void processReplyQueue() {
        if (isSending || replyQueue.isEmpty()) return;
        isSending = true;
        Runnable task = replyQueue.poll();
        if (task != null) {
            task.run();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isSending = false;
                processReplyQueue();
            }, 2500);
        } else {
            isSending = false;
        }
    }

    private static String escapeJs(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private static void safeEvaluateJs(String js) {
        if (hiddenWebView != null && hiddenWebView.getParent() != null) {
            hiddenWebView.post(() -> hiddenWebView.evaluateJavascript(js, null));
        }
    }

    // =========================================================
    // SAFE RELOAD 
    // =========================================================
    public static void safeReload() {
        long now = System.currentTimeMillis();
        if (now - firstReloadInWindow > 60000) {
            reloadCountWindow = 0;
            firstReloadInWindow = now;
        }
        if (reloadCountWindow >= 3) {
            Log.e(TAG, "ANTI-LOOP: Blocked too many reloads!");
            return;
        }
        if (now - lastReloadTime > 15000) {
            Log.w(TAG, "PERFORMING SAFE RELOAD...");
            reloadCountWindow++;
            if (hiddenWebView != null) {
                hiddenWebView.post(() -> hiddenWebView.reload());
            }
            lastReloadTime = now;
        }
    }

    // =========================================================================
    // NÂNG CẤP ĐỘNG CƠ: HYBRID VISION-API AUTOMATION ENGINE (TẦNG 1 & TẦNG 3)
    // =========================================================================
    public static void sendReplyToSpecificMessage(
            final Activity activity,
            final String conversationId,
            final String msgId,
            final String text,
            final String msgTextToFind,
            final String sentTime) {

        Activity safeActivity = activityRef != null ? activityRef.get() : activity;
        if (safeActivity == null || hiddenWebView == null) return;

        replyQueue.add(() -> safeActivity.runOnUiThread(() -> {
            try {
                String safeReply = escapeJs(text);
                String safeSearchText = escapeJs(msgTextToFind != null ? msgTextToFind : "");
                String safeMsgId = escapeJs(msgId != null ? msgId : "");
                String safeTime = escapeJs(sentTime != null ? sentTime : "");
                String safeConvId = escapeJs(conversationId != null ? conversationId : "");

                String jsCode =
                "(function() {" +
                "try {" +
                
                // =========================================================================
                // 1. WATCHDOG & WEBPACK HIJACKING: TỰ ĐỘNG DÒ CỔNG API CORE NGẦM (TẦNG 3)
                // =========================================================================
                "   window.zautoDiscoverMessengerApi = function() {" +
                "       if (window.zauto_api_hijacked && window.zMessenger) return true;" +
                "       if (window._zauto_webpack_pushed) return false;" +  
                "       window._zauto_webpack_pushed = true;" +
                "       try {" +
                "           let chunks = window.webpackChunkzalo_web_app || window.webpackJsonp;" +
                "           if (!chunks) return false;" +
                "           let spyId = 'zauto_spy_core';" +  
                "           let req = null;" +
                "           chunks.push([[spyId], { [spyId]: function(m, e, r) { req = r; } }, [[spyId]]]);" +
                "           if (!req || !req.m) return false;" +
                "           for (let id in req.m) {" +
                "               try {" +
                "                   let mod = req(id);" +
                "                   let exp = mod && (mod.default || mod);" +
                "                   if (exp && typeof exp.sendMessage === 'function' && typeof exp.sendTextMessage === 'function') {" +
                "                       window.zMessenger = exp;" +
                "                       window.zauto_api_hijacked = true;" +
                "                       console.log('ZAuto API Linked: ' + id);" +
                "                       break;" +
                "                   }" +
                "               } catch(e) { continue; }" +
                "           }" +
                "       } catch(err) { console.log('Webpack error:', err); }" +
                "       return window.zauto_api_hijacked;" +
                "   };" +
                "   window.zautoDiscoverMessengerApi();" +

                "   var convId = '" + safeConvId + "';" +
                "   var safeReply = '" + safeReply + "';" +
                "   var safeSearchText = '" + safeSearchText + "';" +
                "   var targetMsgId = '" + safeMsgId + "';" +
                "   var targetTime = '" + safeTime + "';" +

                // BẢO LƯU HOÀN TOÀN CƠ CHẾ CHUYỂN NHÓM TRÊN GIAO DIỆN CŨ
                // FIX: Xóa toàn bộ \"
                "   function openGroup(callback) {" +
                "       if (!convId || convId === '') { callback(true); return; }" +
                "       var groupItem = document.querySelector('.msg-item[anim-data-id=' + convId + '] .conv-item') || document.querySelector('.msg-item[anim-data-id=' + convId + ']') || document.querySelector('[id*=' + convId + ']');" +
                "       if (!groupItem) {" +
                "           var allConvItems = document.querySelectorAll('.msg-item, .conv-item');" +
                "           for (var i = 0; i < allConvItems.length; i++) {" +
                "               var el = allConvItems[i];" +
                "               var rKey = Object.keys(el).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactProps'));" +
                "               if (rKey && el[rKey]) {" +
                "                   var nodeVal = el[rKey];" +
                "                   if (nodeVal) {" +
                "                       var p = nodeVal.memoizedProps || nodeVal.pendingProps;" +
                "                       if (p && ((p.session && String(p.session.id) === convId) || (p.convId && String(p.convId) === convId))) { groupItem = el; break; }" +
                "                   }" +
                "               }" +
                "           }" +
                "       }" +
                "       if (!groupItem) { callback(false); return; }" +
                "       groupItem.scrollIntoView({block: 'center'}); groupItem.click();" +
                "       var key = Object.keys(groupItem).find(k => k.startsWith('__reactEventHandlers') || k.startsWith('__reactFiber'));" +
                "       if (key && groupItem[key]) {" +
                "           var handler = groupItem[key].onClick || (groupItem[key].return && groupItem[key].return.memoizedProps && groupItem[key].return.memoizedProps.onClick);" +
                "           if (handler) handler({preventDefault:()=>{}, stopPropagation:()=>{}});" +
                "       }" +
                "       setTimeout(() => callback(true), 2500);" +
                "   }" +

                // BẢO LƯU HOÀN TOÀN LOGIC PHÂN TÍCH VÀ ĐỊNH VỊ TIN NHẮN THEO HARDWARE ID VÀ VOICE
                // FIX: Xóa toàn bộ \"
                "   function findTargetMessage() {" +
                "       var targetNode = null;" +
                "       if (targetMsgId && !targetMsgId.startsWith('TIME_') && targetMsgId.length > 3) {" +
                "           targetNode = document.querySelector('[data-msg-id=' + targetMsgId + ']') || document.querySelector('div[id*=' + targetMsgId + ']');" +
                "       }" +
                "       if (!targetNode) {" +
                "           var isVoice = safeSearchText.toLowerCase().includes('tin nh\\u1eafn tho\\u1ea1i') || safeSearchText.toLowerCase().includes('[tin nh\\u1eafn tho\\u1ea1i]') || safeSearchText.toLowerCase().includes('ghi \\u00e2m') || safeSearchText.toLowerCase().includes('voice') || safeSearchText.toLowerCase().includes('audio');" +
                "           var allItems = document.querySelectorAll('.chat-item, .message-item, [class*=message], [class*=chat-bubble], div[id^=msg_], div[id^=msg-]');" +
                "           for (var i = allItems.length - 1; i >= 0; i--) {" +
                "               var el = allItems[i];" +
                "               var elHtml = el.innerHTML ? el.innerHTML.toLowerCase() : '';" +
                "               if (isVoice) {" +
                "                   if (elHtml.includes('audio') || elHtml.includes('ico-voice') || elHtml.includes('fa-playcircle') || elHtml.includes('v-audio')) { targetNode = el; break; }" +
                "               } else if (safeSearchText.length > 2) {" +
                "                   var elText = (el.innerText || el.textContent || '').trim();" +
                "                   if (elText.includes(safeSearchText)) { targetNode = el; break; }" +
                "               }" +
                "           }" +
                "       }" +
                "       if (!targetNode) {" +
                "           var allMsgs = document.querySelectorAll('div[id^=msg_], div[id^=msg-], .chat-item, .message-item');" +
                "           if (allMsgs.length > 0) targetNode = allMsgs[allMsgs.length - 1];" +
                "       }" +
                "       return targetNode;" +
                "   }" +

                // =========================================================================
                // 2. HÀM ĐIỀU PHỐI CHỐT CUỐC - NÂNG CẤP: FIBER ĐI NGƯỢC + TỌA ĐỘ VÙNG TRỐNG THÔNG MINH
                // =========================================================================
                "   function executeSend(targetNode) {" +

                // --- LẤY METADATA ĐI NGƯỢC LÊN CHA (CHÍNH XÁC HƠN ĐI XUỐNG CON) ---
                "       let realQuoteId = targetMsgId;" +
                "       let msgReactObj = null;" +
                "       if (targetNode) {" +
                "           targetNode.scrollIntoView({block:'center', behavior:'smooth'});" +
                // Cách 1: đi ngược lên 10 tầng cha qua fiber.return
                "           try {" +
                "               let fKey = Object.keys(targetNode).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactProps'));" +
                "               if (fKey && targetNode[fKey]) {" +
                "                   let fNode = targetNode[fKey];" +
                "                   for (let i = 0; i < 10; i++) {" +
                "                       if (!fNode) break;" +
                "                       let p = fNode.memoizedProps || fNode.pendingProps;" +
                "                       if (p) {" +
                "                           let o = p.msg || p.message || p.data || p.item;" +
                "                           if (o && typeof o === 'object' && (o.msgId || o.globalMsgId || o.cliMsgId)) {" +
                "                               realQuoteId = String(o.msgId || o.globalMsgId || o.cliMsgId);" +
                "                               msgReactObj = o;" +
                "                               break;" +
                "                           }" +
                "                       }" +
                "                       fNode = fNode.return;" +
                "                   }" +
                "               }" +
                "           } catch(e) {}" +
                // Cách 2 fallback: quét xuống các con
                "           if (!msgReactObj) {" +
                "               let allNodes = Array.from(targetNode.querySelectorAll('*'));" +
                "               for (let n of allNodes) {" +
                "                   let rk = Object.keys(n).find(k => k.startsWith('__reactFiber') || k.startsWith('__reactProps'));" +
                "                   if (rk && n[rk]) {" +
                "                       let p = n[rk].memoizedProps || n[rk].pendingProps || {};" +
                "                       let o = p.msg || p.message || p.data || p.item;" +
                "                       if (o && typeof o === 'object') {" +
                "                           let fid = o.msgId || o.messageId || o.cliMsgId || o.globalMsgId;" +
                "                           if (fid && String(fid).length > 4) {" +
                "                               realQuoteId = String(fid); msgReactObj = o; break;" +
                "                           }" +
                "                       }" +
                "                   }" +
                "               }" +
                "           }" +
                "           if (realQuoteId && realQuoteId.length > 4) realQuoteId = realQuoteId.replace('msg-','').replace('msg_','');" +
                "       }" +

                // --- TẦNG 0: LẤY THẲNG TỪ REACT CONVERSATION STORE ---
                "       if (!msgReactObj && targetMsgId && targetMsgId.length > 3 && !targetMsgId.startsWith('TIME_')) {" +
                "           try {" +
                "               let searchRoots = [document.getElementById('app'), document.querySelector('#main-app'), document.querySelector('[class*=chat-window]'), document.querySelector('[class*=conversation]')].filter(Boolean);" +
                "               outer: for (let root of searchRoots) {" +
                "                   let rk = Object.keys(root).find(k => k.startsWith('__reactFiber'));" +
                "                   if (!rk) continue;" +
                "                   let node = root[rk]; let depth = 0;" +
                "                   while (node && depth < 80) {" +
                "                       let ms = node.memoizedState;" +
                "                       if (ms) {" +
                "                           let arr = ms.memoizedState || ms.queue;" +
                "                           if (Array.isArray(arr)) {" +
                "                               for (let m of arr) {" +
                "                                   if (m && typeof m === 'object') {" +
                "                                       let fid = String(m.msgId || m.globalMsgId || m.cliMsgId || '');" +
                "                                       if (fid === targetMsgId || fid === realQuoteId) {" +
                "                                           msgReactObj = m; realQuoteId = fid; break outer;" +
                "                                       }" +
                "                                   }" +
                "                               }" +
                "                           }" +
                "                       }" +
                "                       node = node.child || node.sibling || (node.return ? node.return.sibling : null);" +
                "                       depth++;" +
                "                   }" +
                "               }" +
                "           } catch(e) { console.log('Tang0 err:', e); }" +
                "       }" +

                // --- TẦNG 1: API CORE (nhanh nhất, không cần click đúp) ---
                "       let apiAlive = false;" +
                "       try { apiAlive = window.zMessenger && typeof window.zMessenger.sendMessage === 'function'; } catch(e) {}" +
                "       if (!apiAlive) {" +
                "           window.zauto_api_hijacked = false; window._zauto_webpack_pushed = false;" +
                "           if (typeof window.zautoDiscoverMessengerApi === 'function') window.zautoDiscoverMessengerApi();" +
                "       }" +
                "       if (window.zMessenger && typeof window.zMessenger.sendMessage === 'function') {" +
                "           try {" +
                "               let reqObj = { toid: convId, msg: safeReply, type: 1 };" +
                "               if (realQuoteId && !realQuoteId.startsWith('TIME_') && msgReactObj) {" +
                "                   reqObj.quote = {" +
                "                       globalMsgId: realQuoteId," +
                "                       ownerId: msgReactObj.ownerId || msgReactObj.senderId || msgReactObj.uid || ''," +
                "                       dName: msgReactObj.dName || msgReactObj.senderName || msgReactObj.fromName || ''," +
                "                       msg: msgReactObj.content || msgReactObj.msg || msgReactObj.text || safeSearchText," +
                "                       type: msgReactObj.msgType || msgReactObj.type || 1" +
                "                   };" +
                "               } else if (realQuoteId && !realQuoteId.startsWith('TIME_')) {" +
                "                   reqObj.quote = { globalMsgId: realQuoteId, msg: safeSearchText, type: 1 };" +
                "               }" +
                "               window.zMessenger.sendMessage(reqObj);" +
                "               ZAutoBridge.onLoginSuccess('Chốt API QUOTE OK', '');" +
                "               return;" +
                "           } catch(apiErr) { console.log('API lỗi, chuyển Tầng 2'); }" +
                "       }" +

                // --- TẦNG 2: DOM UI - CLICK ĐÚP THÔNG MINH THEO VỊ TRÍ TIN NHẮN NHÓM/2 NGƯỜI ---
                "       try {" +
                "           if (targetNode) {" +
                "               let bubble = targetNode.querySelector('.card--text, .card-content, div[class*=bubble], .message-chat-inner, [class*=chat-item__content]') || targetNode;" +
                "               let quoteSuccess = false;" +

                // Ưu tiên 1: Chọc thẳng vào React fiber handler (không cần tọa độ chuột)
                "               let nodesToTry = [bubble, targetNode, targetNode.querySelector('.chat-message')].filter(Boolean);" +
                "               for (let n of nodesToTry) {" +
                "                   let rk = Object.keys(n).find(k => k.startsWith('__reactEventHandlers') || k.startsWith('__reactFiber'));" +
                "                   if (rk && n[rk]) {" +
                "                       let dbl = n[rk].onDoubleClick || (n[rk].return && n[rk].return.memoizedProps && n[rk].return.memoizedProps.onDoubleClick);" +
                "                       if (dbl) { dbl({preventDefault:()=>{}, stopPropagation:()=>{}, bubbles:true}); quoteSuccess = true; break; }" +
                "                   }" +
                "               }" +

                // Ưu tiên 2: MouseEvent tọa độ thông minh
                "               if (!quoteSuccess) {" +
                "                   let rect = bubble.getBoundingClientRect();" +
                "                   if (rect.width > 0 && rect.height > 0) {" +
                "                       let screenW = window.innerWidth || document.documentElement.clientWidth;" +
                "                       let cy = rect.top + (rect.height / 2);" +
                "                       let cx;" +
                "                       if (rect.left < screenW / 2) {" +
                "                           cx = Math.min(rect.right + 30, screenW - 15);" +
                "                       } else {" +
                "                           cx = Math.max(rect.left - 30, 15);" +
                "                       }" +
                "                       let wrapper = targetNode.closest('.chat-message, .message-container, [class*=message-row]') || targetNode;" +
                "                       wrapper.dispatchEvent(new MouseEvent('dblclick', {bubbles:true, cancelable:true, view:window, clientX:cx, clientY:cy, buttons:1}));" +
                "                   }" +
                "               }" +
                "           }" +

                // Đợi Zalo bật thanh "Đang trả lời" rồi gõ và gửi
                "           setTimeout(() => {" +
                "               var input = document.querySelector('#richInput') || document.querySelector('[contenteditable=true]') || document.querySelector('.chat-input');" +
                "               if (!input) { ZAutoBridge.onLoginSuccess('TRIGGER_VISION_FALLBACK', realQuoteId); return; }" +
                "               input.setAttribute('readonly','true'); input.focus(); input.removeAttribute('readonly'); input.innerHTML = safeReply;" +
                "               input.dispatchEvent(new Event('input', {bubbles:true}));" +
                "               setTimeout(() => {" +
                "                   var btnSend = document.querySelector('#chat-input-container-id .send-msg-btn') || document.querySelector('.fa-Sent-msg_24_Line') || document.querySelector('[data-translate-title=STR_SEND]');" +
                "                   if (btnSend) { var tgt = btnSend.closest('.z--btn--v2') || btnSend.parentNode || btnSend; tgt.click(); }" +
                "                   input.dispatchEvent(new KeyboardEvent('keydown', {bubbles:true, cancelable:true, keyCode:13, key:'Enter'}));" +
                "                   setTimeout(() => {" +
                "                       var chk = document.querySelector('#richInput') || document.querySelector('[contenteditable=true]');" +
                "                       var sent = !chk || chk.innerHTML==='' || chk.innerHTML==='<br>' || chk.textContent.trim()==='';" +
                "                       ZAutoBridge.onLoginSuccess(sent ? 'Chốt DOM UI QUOTE OK' : 'TRIGGER_VISION_FALLBACK', sent ? '' : realQuoteId);" +
                "                   }, 400);" +
                "               }, 300);" +
                "           }, 600);" +
                "       } catch(domErr) {" +
                "           ZAutoBridge.onLoginSuccess('TRIGGER_VISION_FALLBACK', realQuoteId);" +
                "       }" +
                "   }" +

                // LUỒNG KHỞI CHẠY CHÍNH
                "   openGroup(function(opened) {" +
                "       var targetNode = findTargetMessage();" +
                "       executeSend(targetNode);" +
                "   });" +

                "} catch(e) { console.log('ZAuto Reply Error:', e); }" +
                "})();";

                hiddenWebView.evaluateJavascript(jsCode, null);
            } catch (Exception e) {
                Log.e(TAG, "Reply Engine Error", e);
            }
        }));

        safeActivity.runOnUiThread(ZaloWebManager::processReplyQueue);
    }

    // =========================================================
    // JAVA BRIDGE → NÉM VÀO RAM PYTHON THAY VÌ BROADCAST
    // =========================================================
    public static class WebAppInterface {
        Context mContext;
        WebAppInterface(Context c) { mContext = c; }

        @JavascriptInterface
        public void onHeartbeat(String ts) {
            lastHeartbeat = System.currentTimeMillis();
        }

        @JavascriptInterface
        public void onLoginSuccess(String name, String avatar) {
            pythonMsgQueue.add("LOGIN_SUCCESS|||" + name + "|||" + avatar);
        }

        @JavascriptInterface
        public void onNewWebMsg(String group, String msg, String msgId, String conversationId) {
            pythonMsgQueue.add("WEB_NEW_MSG|||" + group + "|||" + msg + "|||" + msgId + "|||" + conversationId);
        }

        @JavascriptInterface
        public void onGroupListReceived(String jsonGroups) {
            pythonMsgQueue.add("GROUPS_DATA|||" + jsonGroups);
        }
		@JavascriptInterface
        public void onLogout() {
            pythonMsgQueue.add("ZALO_LOGOUT|||");
        }
    }

    // =========================================================
    // KHỞI TẠO WEBVIEW TƯƠNG THÍCH MỌI PHIÊN BẢN ANDROID 10 -> 15
    // =========================================================
    public static void initWebView(final Activity activity) {
        if (activity == null) return;
        activityRef = new WeakReference<>(activity);

        activity.runOnUiThread(() -> {
            try {
                if (hiddenWebView != null) return;

                webLayout = new FrameLayout(activity);
                hiddenWebView = new WebView(activity);

                // =========================================================
                // CẤU HÌNH LAYER TYPE - ÉP DÙNG HARDWARE ĐỂ VẼ QR CANVAS
                // =========================================================
                hiddenWebView.setLayerType(View.LAYER_TYPE_NONE, null);

                // Android 8+ trở lên: Ưu tiên tài nguyên hệ thống cho render process
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    hiddenWebView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true);
                }

                // =========================================================
                // CẤU HÌNH WEBSETTINGS TƯƠNG THÍCH MỌI PHIÊN BẢN CHROME CORE
                // =========================================================
                WebSettings settings = hiddenWebView.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setDatabaseEnabled(true);
                settings.setAllowFileAccess(true);
                settings.setLoadsImagesAutomatically(true);
                settings.setMediaPlaybackRequiresUserGesture(false);
                settings.setCacheMode(WebSettings.LOAD_DEFAULT);
                settings.setNeedInitialFocus(false);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
                settings.setUseWideViewPort(true);
                settings.setLoadWithOverviewMode(true);
                
                // BẬT ZOOM NGẦM ĐỂ TRÁNH CSS ẨN KHUNG QR ĐĂNG NHẬP
                settings.setSupportZoom(true);
                settings.setBuiltInZoomControls(true);
                settings.setDisplayZoomControls(false);

                // OffscreenPreRaster: Chỉ kích hoạt trên Android 11+ nhằm tối ưu tải trang ngầm
                settings.setOffscreenPreRaster(true);

                // ForceDark: Tắt chế độ tối trên Android 10+ để mã QR hiển thị rõ nét nhất
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    settings.setForceDark(WebSettings.FORCE_DARK_OFF);
                }

                // =========================================================
                // ⚠️ BẮT BUỘC GIỮ USER AGENT DESKTOP
                // Để Zalo luôn kích hoạt phiên bản máy tính phục vụ cào quét tin ngầm
                // =========================================================
                settings.setUserAgentString(
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/136.0.0.0 Safari/537.36"
                );

                // =========================================================
                // QUẢN LÝ COOKIE THÀNH PHẦN THỨ BA
                // =========================================================
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.setAcceptCookie(true);
                cookieManager.setAcceptThirdPartyCookies(hiddenWebView, true);

                hiddenWebView.addJavascriptInterface(new WebAppInterface(activity), "ZAutoBridge");
                hiddenWebView.setWebChromeClient(new WebChromeClient());

                // =========================================================
                // WEBVIEW CLIENT: XỬ LÝ LỖI MẠNG VÀ INJECT JS TỰ ĐỘNG
                // =========================================================
                hiddenWebView.setWebViewClient(new WebViewClient() {
                    @Override
                    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                        if (request != null && request.isForMainFrame()) {
                            Log.e(TAG, "Lỗi kết nối Zalo: " + error.getDescription());
                            view.postDelayed(() -> {
                                if (view != null) safeReload();
                            }, 5000);
                        }
                    }

                    @Override
                    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                        // VÁ LỖI ANDROID 10: Bỏ qua lỗi bắt tay SSL do chứng chỉ trung gian Let's Encrypt hết hạn trên máy cũ
                        handler.proceed();
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        CookieManager.getInstance().flush();
                        
                        view.postDelayed(() -> {
                            try {
                                if (hiddenWebView != null) injectSidebarObserver(hiddenWebView);
                            } catch (Exception e) {
                                Log.e(TAG, "Inject Error", e);
                            }
                        }, 5000);
                    }
                    
                    @Override
                    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                        Log.e(TAG, "WEBVIEW RENDER DEAD. RECOVERING...");
                        if (webLayout != null) webLayout.removeAllViews();
                        if (hiddenWebView != null) {
                            hiddenWebView.destroy();
                            hiddenWebView = null;
                        }
                        Activity act = activityRef != null ? activityRef.get() : null;
                        if (act != null) {
                            // Chờ 2 giây giải phóng luồng cũ rồi tự động khởi tạo lại hệ thống ngầm
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                initWebView(act);
                            }, 2000);
                        }
                        return true;
                    }
                });

                hiddenWebView.loadUrl("https://id.zalo.me/account?continue=https://chat.zalo.me");

                FrameLayout.LayoutParams webParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                webLayout.addView(hiddenWebView, webParams);
                // Đẩy webLayout ra tọa độ âm (ngoài màn hình) ngay từ đầu để không che Kivy
				FrameLayout.LayoutParams rootParams = new FrameLayout.LayoutParams(1080, 2400);
				rootParams.leftMargin = -2000; 
				rootParams.topMargin = -2000;

				webLayout.setAlpha(0.01f);  
				webLayout.setTranslationZ(-100f); 

				if (webLayout.getParent() != null) {  
					((ViewGroup) webLayout.getParent()).removeView(webLayout);
				}

				activity.addContentView(webLayout, rootParams);

                // Chỉ bật visible cho WebView, không gọi bringToFront
                hiddenWebView.setVisibility(View.VISIBLE);

                // KHỞI TẠO GIỌNG NÓI TIẾNG VIỆT CÓ BẢO VỆ FALLBACK
                if (tts == null) {
                    tts = new TextToSpeech(activity.getApplicationContext(), status -> {
                        if (status == TextToSpeech.SUCCESS) {
                            int result = tts.setLanguage(new Locale("vi", "VN"));
                            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                // Nếu máy thiếu dữ liệu gói tiếng Việt, tự động lùi về ngôn ngữ mặc định của máy để tránh câm loa
                                tts.setLanguage(Locale.getDefault());
                                Log.w(TAG, "TTS: Thừa dữ liệu tiếng Việt, lùi về ngôn ngữ hệ thống thiết bị.");
                            }
                        }
                    });
                }

                startWatchdog();

            } catch (Exception e) {
                Log.e(TAG, "Init Error", e);
            }
        });
    }

    // =========================================================
    // JS OBSERVER & API INTERNAL (CHẠY NGẦM 100%)
    // =========================================================
    public static void injectSidebarObserver(WebView view) {
        String js =
            "(function() {" +
            "   if(window.zauto_started) return;" +
            "   window.zauto_started = true;" +
            "   window.zauto_boot_time = Date.now();" +
            "   window.zauto_seen = {};" +
            "   window.zauto_seen_keys = [];" +

            // HÀM GỬI REPLY ĐA TẦNG (DÙNG SELECTOR CHUẨN + ENTER + BÁO CÁO TOAST)
            "   window.zautoSendReply = function(convId, fakeMsgId, text, groupName) {" +
            "       try {" +
            "           let item = document.querySelector('.msg-item[anim-data-id=' + convId + '] .conv-item');" +
            "           if(item) {" +
            "               let key = Object.keys(item).find(k => k.startsWith('__reactEventHandlers') || k.startsWith('__reactFiber'));" +
            "               if (key && item[key]) {" +
            "                   if (item[key].onClick) item[key].onClick({preventDefault:()=>{}, stopPropagation:()=>{}});" +
            "                   else if (item[key].return && item[key].return.memoizedProps.onClick) item[key].return.memoizedProps.onClick({preventDefault:()=>{}, stopPropagation:()=>{}});" +
            "               } else { item.click(); }" +
            "           }" +
            
            "           setTimeout(() => {" +
            "               let realMsgId = '';" +
            "               try {" +
            "                   let msgs = document.querySelectorAll('[id^=msg_]');" +
            "                   if (msgs && msgs.length > 0) {" +
            "                       let lastMsg = msgs[msgs.length - 1];" +
            "                       realMsgId = lastMsg.getAttribute('id').substring(4);" +
            "                   }" +
            "               } catch(err) { realMsgId = ''; }" +
            
            "               if (window.zMessenger && typeof window.zMessenger.sendMessage === 'function') {" +
            "                   let req = { toid: convId, msg: text, type: 1 };" +
            "                   if (realMsgId && realMsgId !== '') req.quote = { globalMsgId: realMsgId, msg: text, type: 1 };" +
            "                   window.zMessenger.sendMessage(req);" +
            "                   ZAutoBridge.onLoginSuccess('Đã chốt xong:', groupName);" + 
            "               } else {" +
            "                   let input = document.getElementById('richInput');" +
			"                   if(input) {" +
			"                       input.setAttribute('readonly','true'); input.focus(); input.removeAttribute('readonly');" +
			"                       input.innerHTML = '';" +
			"                       document.execCommand('insertText', false, text);" +
			"                       input.dispatchEvent(new Event('input', {bubbles:true}));" +
			"                       input.blur();" +
            "                       let attempts = 0;" +
            
            "                       let trySend = setInterval(() => {" +
            "                           attempts++;" +
            "                           let btnSend = null;" +
            "                           let primarySelector = '#chat-input-container-id > div.chat-input-container__right-layout > div.normal-buttons-group > div.send-msg-btn';" +
            "                           let fallbackSelectors = ['.fa-Sent-msg_24_Line', '[data-translate-title=STR_SEND]'];" +
            
            "                           let el = document.querySelector(primarySelector);" +
            "                           if (el) {" +
            "                               btnSend = el;" +
            "                           } else {" +
            "                               for (let sel of fallbackSelectors) {" +
            "                                   let fallbackEl = document.querySelector(sel);" +
            "                                   if (fallbackEl) {" +
            "                                       btnSend = fallbackEl.closest('.z--btn--v2') || fallbackEl.parentElement || fallbackEl;" +
            "                                       break;" +
            "                                   }" +
            "                               }" +
            "                           }" +
            
            "                           if (btnSend) {" +
            "                               btnSend.click();" +
            "                               let key = Object.keys(btnSend).find(k => k.startsWith('__reactEventHandlers') || k.startsWith('__reactFiber'));" +
            "                               if(key && btnSend[key] && btnSend[key].onClick) btnSend[key].onClick({preventDefault:()=>{}, stopPropagation:()=>{}});" +
            "                           }" +
            
            "                           let enterEvent = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, keyCode: 13, which: 13, key: 'Enter', code: 'Enter' });" +
            "                           input.dispatchEvent(enterEvent);" +
            
            "                           if (input.innerHTML === '' || input.innerHTML === '<br>') {" +
            "                               clearInterval(trySend);" +
            "                               ZAutoBridge.onLoginSuccess('Đã chốt xong:', groupName);" + 
            "                           } else if (attempts > 12) {" +
            "                               clearInterval(trySend);" +
            "                           }" +
            "                       }, 250);" + 
            "                   }" +
            "               }" +
            "           }, 800);" + 
            "       } catch(e) {}" +
            "   };" +

            // HÀM QUÉT SIDEBAR (ĐÃ FIX ZALO ĐỔI GIAO DIỆN & TÌM ĐÚNG ID TIN)
            "   function scanConvItem(msgItemEl) {" +
            "       try {" +
            "           let convItem = msgItemEl.querySelector('.conv-item') || msgItemEl;" + 
            "           if(!convItem) return;" +
            
            "           let nameEl = convItem.querySelector('.conv-item-title__name, [class*=name]');" +
            "           let bodyEl = convItem.querySelector('.conv-item-body, [class*=snippet], [class*=message-text]');" +
            "           if(!nameEl) return;" + 
            "           let groupName = (nameEl.textContent || nameEl.innerText || '').trim();" +
            "           let msgText = bodyEl ? (bodyEl.textContent || bodyEl.innerText || '').trim() : '';" +
            
            "           let realMsgId = ''; var fullTxt = ''; let convId = '';" +
            
            // DEEP BYPASS: QUÉT VÉT CẠN MỌI TẦNG REACT FIBER (8 TẦNG) ĐỂ LỘT TRẦN ID BỊ GIẤU
            "           try {" +
            "               convId = msgItemEl.getAttribute('anim-data-id') || msgItemEl.id || '';" +
            "               let id1 = msgItemEl.getAttribute('data-msg-id') || (msgItemEl.dataset ? msgItemEl.dataset.msgId : '');" +
            "               if (id1 && id1.length > 5) realMsgId = id1;" +
            
            "               let keys = Object.keys(msgItemEl);" +
            "               let rK = keys.find(k => k.startsWith('__reactFiber') || k.startsWith('__reactProps'));" +
            "               if (rK && msgItemEl[rK]) {" +
			"                   let node = msgItemEl[rK];" +
			"                   if (node) {" +
			"                       let p = node.memoizedProps || node.pendingProps;" +
			"                       if(!convId && p && p.session) { convId = String(p.session.id); }" +
			"                       if(!convId && p && p.convId) { convId = String(p.convId); }" +
			"                   }" +
			"                   for(let step = 0; step < 8; step++) {" + 
            "                       if(!node) break;" +
            "                       let currProps = node.memoizedProps || node.pendingProps;" +
            "                       if (currProps) {" +
            "                           let o = currProps.msg || currProps.message || currProps.data || currProps.item || currProps;" +
            "                           if (o && typeof o === 'object') {" +
            "                               let foundId = o.msgId || o.messageId || o.cliMsgId || o.globalMsgId;" +
            "                               if (!foundId && o.timestamp) foundId = 'TS_' + o.timestamp;" + 
            "                               if (!realMsgId && foundId && String(foundId).length > 5) { realMsgId = String(foundId); }" +
            "                               if (!fullTxt && typeof o.content === 'string' && o.content.trim() !== '') { fullTxt = o.content; }" +
            "                           }" +
            "                       }" +
            "                       node = node.return;" +
            "                   }" +
            "               }" +
            "           } catch(err) {}" +
            
            "           if (fullTxt && fullTxt.length > msgText.length && !fullTxt.startsWith('{')) {" +
            "               msgText = fullTxt.trim();" +
            "           }" +
            
            "           let isVoiceNode = bodyEl ? bodyEl.querySelector('[class*=audio],[class*=voice],[class*=Voice],[class*=record],[class*=AudioMessage],[class*=VoiceMsg]') : null;" +
            "           if (!isVoiceNode && bodyEl) { let svgs = bodyEl.querySelectorAll('svg'); for (let s of svgs) { if (s.closest('[class*=audio],[class*=voice],[class*=record],[class*=Voice]')) { isVoiceNode = s; break; } } }" +
            "           let isTimeOnly = bodyEl ? (/^[0-9]{1,2}:[0-9]{2}$/.test(msgText) || /^[0-9]{1,2}:[0-9]{2}$/.test(bodyEl.innerText.trim())) : false;" + 
            "           let seconds = -1; let isVoice = false;" +
            
            "           if (isVoiceNode || isTimeOnly) {" +
            "               isVoice = true;" +
            "               let rawBody = bodyEl.textContent || bodyEl.innerText || '';" +
            "               if (rawBody.indexOf(': ') > -1) {" +
            "                   msgText = rawBody.split(': ')[0] + ': [Tin nhắn thoại]';" +
            "               } else {" +
            "                   msgText = '[Tin nhắn thoại]';" +
            "               }" +
            "               try {" +
            "                   let timeNode = msgItemEl.querySelector('[class*=audio-time], [class*=duration], span[class*=time]');" +
            "                   if (timeNode) {" +
            "                       let durationText = timeNode.innerText.trim();" + 
            "                       if (durationText.includes(':')) {" +
            "                           let parts = durationText.split(':');" +
            "                           seconds = parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);" +
            "                       }" +
            "                   }" +
            "               } catch(err) { seconds = -1; }" +
            "               msgText = msgText + '%%%' + seconds;" + 
            "           }" +

            "           if(!groupName) return;" +
            "           if(!msgText || msgText.length < 1) {" +
            "               setTimeout(() => { try { scanConvItem(msgItemEl); }catch(e){} }, 1000);" +
            "               return;" +
            "           }" +

            "           let timeEl = convItem.querySelector('.conv-item-title__time, [class*=time]');" +
            "           let timeString = timeEl ? (timeEl.textContent || '').trim() : '';" +

            // =========================================================================
            // FIX LỖI LẶP TIN: Dùng stableId cố định, không phụ thuộc vào đồng hồ TIME_
            // =========================================================================
            "           let stableId;" +
            "           if (realMsgId && realMsgId.length > 3 && !realMsgId.startsWith('TIME_')) {" +
            "               stableId = realMsgId;" +
            "           } else {" +
            "               let contentForHash = msgText.replace(/%%%[-0-9]+$/, '').trim();" +
            "               if (isVoice) {" +
            "                   let voiceKey = (realMsgId && realMsgId.length > 3 && !realMsgId.startsWith('TIME_') && !realMsgId.startsWith('TS_')) ? realMsgId : ('V_' + convId + '_' + msgText.replace(/%%%[-0-9]+$/, '').trim().substring(0, 30));" +
            "                   stableId = 'VOICE_' + voiceKey;" +
            "               } else {" +
            "                   stableId = 'CONTENT_' + convId + '_' + contentForHash.substring(0, 60);" +
            "               }" +
            "           }" +
            "           let fp = 'MSG|' + convId + '|' + stableId;" +

            "           if (!realMsgId || realMsgId === '') {" +
            "               realMsgId = 'TIME_' + timeString;" +
            "           }" +
            "           if(window.zauto_seen[fp]) return;" +
            "           window.zauto_seen[fp] = true;" +
            "           window.zauto_seen_keys.push(fp);" +
            "           if(window.zauto_seen_keys.length > 800) { " +
            "               let old = window.zauto_seen_keys.splice(0, 200); " +
            "               old.forEach(k => { delete window.zauto_seen[k]; });" + 
            "           }" +
            "           if (Date.now() - window.zauto_boot_time > 8000) {" +
            "               ZAutoBridge.onNewWebMsg(groupName, msgText, realMsgId, convId);" +
            "           }" +
            "       } catch(e) {}" +
            "   }" +

            // HÀM THU THẬP DANH SÁCH NHÓM
            "   function collectGroups() {" +
            "       try {" +
            "           let groups = [];" +
            "           let nameEls = document.querySelectorAll('.conv-item-title__name');" +
            "           nameEls.forEach(el => {" +
            "               let n = (el.innerText || el.textContent || '').trim();" +
            "               if(n && n.length > 1 && n.length < 80 && !groups.includes(n)) groups.push(n);" +
            "           });" +
            "           if(groups.length > 0) ZAutoBridge.onGroupListReceived(JSON.stringify(groups));" +
            "       } catch(e) {}" +
            "   }" +

            // OBSERVE CONTAINER SIDEBAR
            "   function startSidebarObserver() {" +
            "       let container = document.getElementById('conversationListId');" +
            "       if(!container) {" +
            "           setTimeout(startSidebarObserver, 1500);" +
            "           return;" +
            "       }" +
            "       if(window.zauto_sidebar_observer) window.zauto_sidebar_observer.disconnect();" +
            "       window.zauto_sidebar_observer = new MutationObserver(mutations => {" +
            "           mutations.forEach(m => {" +
            "               try {" +
            "                   let targetNode = m.target.nodeType === 3 ? m.target.parentNode : m.target;" +
            "                   let msgItem = targetNode.closest('.msg-item');" +
            "                   if(msgItem) scanConvItem(msgItem);" +
            "               } catch(e) {}" +
            "           });" +
            "       });" +
            "       window.zauto_sidebar_observer.observe(container, { childList: true, subtree: true, characterData: true });" +
            "       document.querySelectorAll('.msg-item').forEach(scanConvItem);" +
            "       collectGroups();" +
            "       ZAutoBridge.onLoginSuccess('Đã kết nối', '');" +
            "   }" +

            // WATCHDOG + NÚT ĐỒNG BỘ
            "   function systemWatchdog() {" +
            "       ZAutoBridge.onHeartbeat(Date.now().toString());" +
            "       if(!navigator.onLine) { setTimeout(systemWatchdog, 10000); return; }" +
            "       try {" +
            "           let syncBtn = document.querySelector('.sync-msg-btn');" +
            "           if(!syncBtn) {" +
            "               let btns = document.querySelectorAll('button, div, span');" +
            "               for(let b of btns) { if(b.innerText && (b.innerText.includes('Đồng bộ') || b.innerText.includes('Khôi phục'))) { syncBtn = b; break; } }" +
            "           }" +
            "           if(syncBtn) syncBtn.click();" +
            "       } catch(e) {}" +
            "       let isLoginScreen = document.querySelector('.qrcode') || document.querySelector('.login-container');" +
			"       if(isLoginScreen) {" +
			"           if(!window.zauto_logout_notified) { window.zauto_logout_notified = true; ZAutoBridge.onLogout(); }" +
			"           if(!window.login_start_time) window.login_start_time = Date.now();" +
			"           if(Date.now() - window.login_start_time > 180000) { window.login_start_time = Date.now(); location.reload(); }" +
            "       } else {" +
			"           window.login_start_time = null;" +
			"           window.zauto_logout_notified = false;" +
            "           let container = document.getElementById('conversationListId');" +
            "           if(!container || !window.zauto_sidebar_observer) {" +
            "               window.zauto_sidebar_observer = null;" +
            "               startSidebarObserver();" +
            "           }" +
            "           window.zauto_group_tick = (window.zauto_group_tick || 0) + 1;" +
            "           if(window.zauto_group_tick % 5 === 0) collectGroups();" +
            "       }" +
            "       let nextTick = document.hidden ? 15000 : 3000;" +
            "       setTimeout(systemWatchdog, nextTick);" +
            "   }" +
            "   setInterval(() => { if(document.hidden) return; let container = document.getElementById('conversationListId'); if(container) document.querySelectorAll('.msg-item').forEach(scanConvItem); }, 2000);" +
            "   setTimeout(startSidebarObserver, 1000);" +
            "   setTimeout(systemWatchdog, 3000);" +
            "})();";

        safeEvaluateJs(js);
    }

    // =========================================================
    // JAVA WATCHDOG (kiểm tra heartbeat từ JS)
    // =========================================================
    private static void startWatchdog() {
        if (watchdogHandler == null) watchdogHandler = new Handler(Looper.getMainLooper());
        if (watchdogRunnable != null) watchdogHandler.removeCallbacks(watchdogRunnable);

        watchdogRunnable = new Runnable() {
            @Override
            public void run() {
                if (hiddenWebView != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeat > 60000) {
                        Log.e(TAG, "HEARTBEAT LOST. REQUESTING RELOAD...");
                        safeReload();
                    } else if (now - lastHeartbeat > 20000) {
                        safeEvaluateJs("if(!window.zauto_started) location.reload();");
                    }
                }
                watchdogHandler.postDelayed(this, 15000);
            }
        };
        watchdogHandler.postDelayed(watchdogRunnable, 15000);
    }

    // =========================================================
    // ĐIỀU CHỈNH TOẠ ĐỘ RENDER HIỂN THỊ WEB KHỚP VỚI CÁC TAB KIVY
    // =========================================================
    public static void updateWebViewBounds(
            final Activity activity,
            final int x, final int y,
            final int width, final int height,
            final boolean visible) {

        Activity safeActivity = activityRef != null ? activityRef.get() : activity;
        if (safeActivity == null) return;

        safeActivity.runOnUiThread(() -> {
            try {
                if (webLayout == null) return;
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) webLayout.getLayoutParams();
                if (!visible) {
                    webLayout.setAlpha(0.01f);
                    webLayout.setTranslationZ(-100f);
                    params.leftMargin = -2000;
                    params.topMargin = -2000;
                    params.width = 1080;
                    params.height = 2400;
                } else {
                    // TRẠNG THÁI HIỆN (TAB ZALO): Nổi lên trên đỉnh, hiển thị sắc nét đúng tọa độ Kivy chỉ định
                    webLayout.setAlpha(1.0f);
                    webLayout.setTranslationZ(100f);
                    params.leftMargin = x;
                    params.topMargin = y;
                    params.width = width;
                    params.height = height;
                    if (hiddenWebView != null) {
                        hiddenWebView.invalidate();
                        hiddenWebView.requestLayout();
                    }
                }
                webLayout.setLayoutParams(params);
            } catch (Exception e) {
                Log.e(TAG, "updateWebViewBounds Error", e);
            }
        });
    }
    // HÀM ĐỌC GIỌNG NÓI TỪ PYTHON GỌI XUỐNG
    public static void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
    public static void reloadWeb(final Activity activity) {
        safeReload();
    }

    public static void onResume(final Activity activity) {
        Activity safeActivity = activityRef != null ? activityRef.get() : activity;
        if (safeActivity == null) return;
        safeActivity.runOnUiThread(() -> {
            try {
                if (hiddenWebView != null) hiddenWebView.onResume();
            } catch (Exception ignored) {}
        });
    }

    public static void onPause() {
        try {
            // ĐÃ XÓA LỆNH: hiddenWebView.onPause(); -> Để Javascript vẫn chạy khi app ẩn
        } catch (Exception ignored) {}
    }

    public static void destroy() {
        if (watchdogHandler != null && watchdogRunnable != null) {
            watchdogHandler.removeCallbacks(watchdogRunnable);
        }
        replyQueue.clear();
        if (webLayout != null) {
            webLayout.removeAllViews();
            webLayout = null;
        }
        if (hiddenWebView != null) {
            hiddenWebView.clearHistory();
            hiddenWebView.clearCache(true);
            hiddenWebView.destroy();
            hiddenWebView = null;
        }
        if (activityRef != null) {
            activityRef.clear();
            activityRef = null;
        }
    }

    // =========================================================
    // ẨN BÀN PHÍM CỨNG - GỌI TRƯỚC MỌI THAO TÁC CHỐT CUỐC
    // =========================================================
    public static void hideKeyboard(final Activity activity) {
        Activity safeActivity = activityRef != null ? activityRef.get() : activity;
        if (safeActivity == null) return;
        safeActivity.runOnUiThread(() -> {
            try {
                android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                    safeActivity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                android.view.View decorView = safeActivity.getWindow().getDecorView();
                android.os.IBinder token = decorView.getWindowToken();
                if (imm != null && token != null) {
                    imm.hideSoftInputFromWindow(token, 0);
                }
                if (hiddenWebView != null) {
                    hiddenWebView.evaluateJavascript(
                        "(function(){ try { var a = document.activeElement; if(a && a !== document.body) a.blur(); document.body.focus(); } catch(e){} })();", null);
                }
            } catch (Exception e) {}
        });
    }

    /// =========================================================
    // HỆ THỐNG PHÁT BẢN GHI ÂM CHUẨN XÁC THEO ID (ĐÃ SỬA LỖI NỐI CHUỖI)
    // =========================================================
    public static void playSpecificAudio(final Activity activity, final String conversationId, final String msgId) {
        Activity safeActivity = activityRef != null ? activityRef.get() : activity;
        if (safeActivity == null || hiddenWebView == null) return;

        safeActivity.runOnUiThread(() -> {
            String js = "(function() {" +
                "   console.log('ZAuto: Bat dau tim nut Play cho ' + '" + msgId + "');" +
                "   let item = document.querySelector('.msg-item[anim-data-id=" + conversationId + "] .conv-item');" +
                "   if(item) {" +
                "       let key = Object.keys(item).find(k => k.startsWith('__reactEventHandlers') || k.startsWith('__reactFiber'));" +
                "       if (key && item[key]) {" +
                "           if (item[key].onClick) item[key].onClick({preventDefault:()=>{}, stopPropagation:()=>{}});" +
                "           else if (item[key].return && item[key].return.memoizedProps.onClick) item[key].return.memoizedProps.onClick({preventDefault:()=>{}, stopPropagation:()=>{}});" +
                "       } else { item.click(); }" +
                "   }" +
                "   setTimeout(() => {" +
                "       let findAndPlay = () => {" +
                "           let msgNode = document.querySelector('[data-msg-id=" + msgId + "]');" +
                "           if (!msgNode) msgNode = document.querySelector('div[id*=" + msgId + "]');" +
                "           if (!msgNode) {" +
                "               let allMsgs = document.querySelectorAll('.chat-item');" +
                "               if(allMsgs.length > 0) msgNode = allMsgs[allMsgs.length - 1];" +
                "           }" +
                "           if (!msgNode) return false;" +
                "           let playBtn = msgNode.querySelector('.fa-PlayCircle_24_Filled,[class*=PlayCircle],[class*=play-circle],[class*=PlayCircle24],.v-audio,.icon-play-audio,[class*=AudioPlayer] button,[class*=audio-player] button,[class*=VoiceMessage] button,[class*=voice-message] button,button[class*=play],i[class*=play],div[class*=play-btn],div[class*=btn-play]');" +
                "           if (!playBtn) { let ac = msgNode.querySelector('[class*=audio],[class*=voice],[class*=Voice],[class*=record],[class*=AudioMessage],[class*=VoiceMsg]'); if (ac) { playBtn = ac.querySelector('button,svg,i,div[role=button]') || ac; } }" +
                "           if (!playBtn) { let svgs = msgNode.querySelectorAll('svg'); for (let sv of svgs) { if (sv.closest('[class*=audio],[class*=voice],[class*=record],[class*=Voice]')) { playBtn = sv; break; } } }" +
                "           if(playBtn) {" +
                "               console.log('ZAuto: Da tim thay nut Play!');" +
                "               playBtn.scrollIntoView({block:'center'});" +
                "               playBtn.click();" +
                "               let k = Object.keys(playBtn).find(key => key.startsWith('__reactEventHandlers') || key.startsWith('__reactFiber'));" +
                "               if(k && playBtn[k]) { let h = playBtn[k].onClick || (playBtn[k].return && playBtn[k].return.memoizedProps && playBtn[k].return.memoizedProps.onClick); if(h) h({preventDefault:()=>{}, stopPropagation:()=>{}}); }" +
                "               return true;" +
                "           }" +
                "           console.log('ZAuto: KHONG tim thay nut Play, class:', msgNode.className);" +
                "           return false;" +
                "       };" +
                "       if (!findAndPlay()) {" +
                "           let retryCount = 0;" +
                "           let interval = setInterval(() => {" +
                "               retryCount++;" +
                "               if (findAndPlay() || retryCount > 8) clearInterval(interval);" +
                "           }, 500);" +
                "       }" +
                "   }, 1500);" + 
                "})();";
            hiddenWebView.evaluateJavascript(js, null);
        });
    }
}
