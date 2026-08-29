package com.example.ui.theme

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object AppTranslation {

    fun applyAppLocale(context: Context, audioIndex: Int) {
        val langTag = when (audioIndex) {
            1 -> "en"
            2 -> "bn"
            3 -> "es"
            4 -> "hi"
            5 -> "ja"
            6 -> "fr"
            7 -> "de"
            8 -> "ar"
            else -> ""
        }
        if (langTag.isNotEmpty()) {
            val locale = Locale(langTag)
            Locale.setDefault(locale)
            val config = Configuration(context.resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
        }
    }

    fun getString(key: String, audioIndex: Int): String {
        return when (audioIndex) {
            2 -> getBengali(key)
            3 -> getSpanish(key)
            4 -> getHindi(key)
            5 -> getJapanese(key)
            6 -> getFrench(key)
            7 -> getGerman(key)
            8 -> getArabic(key)
            else -> getEnglish(key)
        }
    }

    private fun getEnglish(key: String): String = when (key) {
        "home" -> "Home"
        "browse" -> "Browse"
        "settings" -> "Settings"
        "history" -> "History"
        "downloads" -> "Downloads"
        "watch_history" -> "Watch History"
        "notifications" -> "Notifications"
        "profile" -> "Profile"
        "admin" -> "Admin"
        "search" -> "Search"
        "sign_in" -> "Sign In"
        "watch_anything" -> "Watch Anything"
        "watch_now" -> "Watch Now"
        "explore_categories" -> "CATEGORIES"
        "view_all" -> "View All"
        "secret_pot_settings" -> "Hot Air"
        "live_tv" -> "Live TV"
        "tv_shows" -> "TV Shows"
        "movies" -> "Movies"
        "k_drama" -> "K-Drama"
        "anime" -> "Anime"
        "language" -> "Language"
        "app_update" -> "App Update"
        "select_language_title" -> "Select Audio & System Language"
        "default_system" -> "Default System"
        "clear_cache" -> "Clear App Cache"
        "account_profile" -> "Account & Profile"
        "movies_anime_hub" -> "Movies & Anime Hub"
        "my_saved_channels" -> "My Saved Channels"
        "streaming_history" -> "Streaming History"
        "clear_all" -> "Clear All"
        else -> key
    }

    private fun getBengali(key: String): String = when (key) {
        "home" -> "হোম"
        "browse" -> "ব্রাউজ"
        "settings" -> "সেটিংস"
        "notifications" -> "নোটিফিকেশন"
        "profile" -> "প্রোফাইল"
        "admin" -> "অ্যাডমিন"
        "search" -> "খুঁজুন"
        "sign_in" -> "সাইন ইন"
        "watch_anything" -> "যেকোনো কিছু দেখুন"
        "watch_now" -> "এখনই দেখুন"
        "explore_categories" -> "ক্যাটাগরি"
        "view_all" -> "সব দেখুন"
        "secret_pot_settings" -> "হট এয়ার"
        "live_tv" -> "লাইভ টিভি"
        "tv_shows" -> "টিভি শো"
        "movies" -> "মুভি"
        "k_drama" -> "কে-ড্রামা"
        "anime" -> "অ্যানিমে"
        "language" -> "ভাষা"
        "app_update" -> "অ্যাপ আপডেট"
        "select_language_title" -> "অডিও ও সিস্টেমের ভাষা নির্বাচন করুন"
        "default_system" -> "ডিফল্ট সিস্টেম"
        "clear_cache" -> "অ্যাপ ক্যাশ মুছুন"
        "account_profile" -> "অ্যাকাউন্ট ও প্রোফাইল"
        "movies_anime_hub" -> "মুভি ও অ্যানিমে হাব"
        "my_saved_channels" -> "আমার সেভ করা চ্যানেল"
        "streaming_history" -> "স্ট্রিম ইতিহাস"
        "downloads" -> "ডাউনলোড"
        "watch_history" -> "ওয়াচ হিস্ট্রি"
        "clear_all" -> "সব মুছুন"
        else -> getEnglish(key)
    }

    private fun getSpanish(key: String): String = when (key) {
        "home" -> "Inicio"
        "browse" -> "Navegar"
        "settings" -> "Ajustes"
        "history" -> "Historial"
        "downloads" -> "Descargas"
        "watch_history" -> "Historial de visualización"
        "notifications" -> "Notificaciones"
        "profile" -> "Perfil"
        "admin" -> "Admin"
        "search" -> "Buscar"
        "sign_in" -> "Iniciar sesión"
        "watch_anything" -> "Ver cualquier cosa"
        "watch_now" -> "Ver ahora"
        "explore_categories" -> "CATEGORÍAS"
        "view_all" -> "Ver todo"
        "secret_pot_settings" -> "Hot Air"
        "live_tv" -> "TV en vivo"
        "tv_shows" -> "Series"
        "movies" -> "Películas"
        "k_drama" -> "K-Drama"
        "anime" -> "Anime"
        "language" -> "Idioma"
        "app_update" -> "Actualización de App"
        "select_language_title" -> "Seleccionar idioma"
        "default_system" -> "Sistema predeterminado"
        "clear_cache" -> "Limpiar caché"
        "account_profile" -> "Cuenta y Perfil"
        "movies_anime_hub" -> "Centro de Películas"
        "my_saved_channels" -> "Mis Canales Guardados"
        "streaming_history" -> "Historial de Reproducción"
        "clear_all" -> "Borrar Todo"
        else -> getEnglish(key)
    }

    private fun getHindi(key: String): String = when (key) {
        "home" -> "होम"
        "browse" -> "ब्राउज़"
        "settings" -> "सेティング्स"
        "history" -> "इतिहास"
        "downloads" -> "डाउनलोड"
        "watch_history" -> "देखे गए वीडियो"
        "notifications" -> "सूचनाएं"
        "profile" -> "प्रोफ़ाइल"
        "admin" -> "एडमिन"
        "search" -> "खोजें"
        "sign_in" -> "साइन इन"
        "watch_anything" -> "कुछ भी देखें"
        "watch_now" -> "अभी देखें"
        "explore_categories" -> "श्रेणियां"
        "view_all" -> "सभी देखें"
        "secret_pot_settings" -> "हॉट एयर"
        "live_tv" -> "लाइव टीवी"
        "tv_shows" -> "टीवी शो"
        "movies" -> "फिल्में"
        "k_drama" -> "के-ड्रामा"
        "anime" -> "एनीमे"
        "language" -> "भाषा"
        "app_update" -> "ऐप अपडेट"
        "select_language_title" -> "ऑडियो और सिस्टम भाषा चुनें"
        "default_system" -> "डिफ़ॉल्ट सिस्टम"
        "clear_cache" -> "ऐप कैश साफ़ करें"
        "account_profile" -> "खाता और प्रोफ़ाइल"
        "movies_anime_hub" -> "मूवी और एनीमे हब"
        "my_saved_channels" -> "मेरे सहेजे गए चैनल"
        "streaming_history" -> "स्ट्रीमिंग इतिहास"
        "clear_all" -> "सभी साफ़ करें"
        else -> getEnglish(key)
    }

    private fun getJapanese(key: String): String = when (key) {
        "home" -> "ホーム"
        "browse" -> "閲覧"
        "settings" -> "設定"
        "history" -> "履歴"
        "downloads" -> "ダウンロード"
        "watch_history" -> "視聴履歴"
        "notifications" -> "通知"
        "profile" -> "プロフィール"
        "admin" -> "管理者"
        "search" -> "検索"
        "sign_in" -> "サインイン"
        "watch_anything" -> "何でも見る"
        "watch_now" -> "今すぐ見る"
        "explore_categories" -> "カテゴリ"
        "view_all" -> "すべて見る"
        "secret_pot_settings" -> "ホットエアー"
        "live_tv" -> "ライブTV"
        "tv_shows" -> "TV番組"
        "movies" -> "映画"
        "k_drama" -> "韓流ドラマ"
        "anime" -> "アニメ"
        "language" -> "言語"
        "app_update" -> "アプリの更新"
        "select_language_title" -> "言語を選択"
        "default_system" -> "システム標準"
        "clear_cache" -> "キャッシュの消去"
        "account_profile" -> "アカウントとプロフィール"
        "movies_anime_hub" -> "映画とアニメハブ"
        "my_saved_channels" -> "保存したチャンネル"
        "streaming_history" -> "視聴履歴"
        "clear_all" -> "すべて消去"
        else -> getEnglish(key)
    }

    private fun getFrench(key: String): String = when (key) {
        "home" -> "Accueil"
        "browse" -> "Parcourir"
        "settings" -> "Paramètres"
        "history" -> "Historique"
        "downloads" -> "Téléchargements"
        "watch_history" -> "Historique de visionnage"
        "notifications" -> "Notifications"
        "profile" -> "Profil"
        "admin" -> "Admin"
        "search" -> "Rechercher"
        "sign_in" -> "Se connecter"
        "watch_anything" -> "Regardez tout"
        "watch_now" -> "Regarder"
        "explore_categories" -> "CATÉGORIES"
        "view_all" -> "Voir tout"
        "secret_pot_settings" -> "Hot Air"
        "live_tv" -> "TV en direct"
        "tv_shows" -> "Séries TV"
        "movies" -> "Films"
        "k_drama" -> "K-Drama"
        "anime" -> "Anime"
        "language" -> "Langue"
        "app_update" -> "Mise à jour"
        "select_language_title" -> "Sélectionner la langue"
        "default_system" -> "Système par défaut"
        "clear_cache" -> "Vider le cache"
        "account_profile" -> "Compte & Profil"
        "movies_anime_hub" -> "Hub Films & Animes"
        "my_saved_channels" -> "Mes chaînes enregistrées"
        "streaming_history" -> "Historique de lecture"
        "clear_all" -> "Tout effacer"
        else -> getEnglish(key)
    }

    private fun getGerman(key: String): String = when (key) {
        "home" -> "Startseite"
        "browse" -> "Durchsuchen"
        "settings" -> "Einstellungen"
        "history" -> "Verlauf"
        "downloads" -> "Downloads"
        "watch_history" -> "Wiedergabeverlauf"
        "notifications" -> "Benachrichtigungen"
        "profile" -> "Profil"
        "admin" -> "Admin"
        "search" -> "Suchen"
        "sign_in" -> "Anmelden"
        "watch_anything" -> "Alles ansehen"
        "watch_now" -> "Jetzt ansehen"
        "explore_categories" -> "KATEGORIEN"
        "view_all" -> "Alle anzeigen"
        "secret_pot_settings" -> "Hot Air"
        "live_tv" -> "Live-TV"
        "tv_shows" -> "TV-Shows"
        "movies" -> "Filme"
        "k_drama" -> "K-Drama"
        "anime" -> "Anime"
        "language" -> "Sprache"
        "app_update" -> "App-Update"
        "select_language_title" -> "Sprache auswählen"
        "default_system" -> "Systemstandard"
        "clear_cache" -> "Cache leeren"
        "account_profile" -> "Konto & Profil"
        "movies_anime_hub" -> "Filme & Anime Hub"
        "my_saved_channels" -> "Gespeicherte Sender"
        "streaming_history" -> "Stream-Verlauf"
        "clear_all" -> "Alles löschen"
        else -> getEnglish(key)
    }

    private fun getArabic(key: String): String = when (key) {
        "home" -> "الرئيسية"
        "browse" -> "تصفح"
        "settings" -> "الإعدادات"
        "history" -> "السجل"
        "downloads" -> "التنزيلات"
        "watch_history" -> "سجل المشاهدة"
        "notifications" -> "الإشعارات"
        "profile" -> "الملف الشخصي"
        "admin" -> "المشرف"
        "search" -> "بحث"
        "sign_in" -> "تسجيل الدخول"
        "watch_anything" -> "شاهد أي شيء"
        "watch_now" -> "شاهد الآن"
        "explore_categories" -> "الفئات"
        "view_all" -> "عرض الكل"
        "secret_pot_settings" -> "هوت إير"
        "live_tv" -> "بث مباشر"
        "tv_shows" -> "برامج تلفزيونية"
        "movies" -> "أفلام"
        "k_drama" -> "دراما كورية"
        "anime" -> "أنمي"
        "language" -> "اللغة"
        "app_update" -> "تحديث التطبيق"
        "select_language_title" -> "اختر اللغة"
        "default_system" -> "النظام الافتراضي"
        "clear_cache" -> "مسح ذاكرة التخزين المؤقت"
        "account_profile" -> "الحساب والملف الشخصي"
        "movies_anime_hub" -> "مركز الأفلام والأنمي"
        "my_saved_channels" -> "قنواتي المحفوظة"
        "streaming_history" -> "سجل المشاهدة"
        "clear_all" -> "مسح الكل"
        else -> getEnglish(key)
    }
}
