import os

# Define the strings translation dictionary
strings_dict = {
    "app_name": {
        "en": "Omni Browser", "es": "Omni Browser", "fr": "Omni Browser", "de": "Omni Browser",
        "hi": "Omni Browser", "pt": "Omni Browser", "ru": "Omni Browser", "zh": "Omni 浏览器", "ja": "Omni ブラウザ"
    },
    "settings_title": {
        "en": "Settings", "es": "Ajustes", "fr": "Paramètres", "de": "Einstellungen",
        "hi": "सेटिंग्स", "pt": "Configurações", "ru": "Настройки", "zh": "设置", "ja": "設定"
    },
    "back_desc": {
        "en": "Back", "es": "Atrás", "fr": "Retour", "de": "Zurück",
        "hi": "पीछे", "pt": "Voltar", "ru": "Назад", "zh": "返回", "ja": "戻る"
    },
    "general_section": {
        "en": "GENERAL", "es": "GENERAL", "fr": "GÉNÉRAL", "de": "ALLGEMEIN",
        "hi": "सामान्य", "pt": "GERAL", "ru": "ОБЩИЕ", "zh": "通用", "ja": "全般"
    },
    "dark_mode_title": {
        "en": "Dark Mode", "es": "Modo oscuro", "fr": "Mode sombre", "de": "Dunkelmodus",
        "hi": "डार्क मोड", "pt": "Modo escuro", "ru": "Темная тема", "zh": "深色模式", "ja": "ダークモード"
    },
    "dark_mode_desc": {
        "en": "Use dark theme across the app", "es": "Usar tema oscuro en la aplicación", "fr": "Utiliser le thème sombre dans l'application", "de": "Dunkles Design in der App verwenden",
        "hi": "ऐप में डार्क थीम का उपयोग करें", "pt": "Usar tema escuro em todo o aplicativo", "ru": "Использовать темную тему в приложении", "zh": "在整个应用中使用深色主题", "ja": "アプリ全体でダークテーマを使用する"
    },
    "accent_color_title": {
        "en": "Accent Color", "es": "Color de acento", "fr": "Couleur d'accentuation", "de": "Akzentfarbe",
        "hi": "एक्सेंट रंग", "pt": "Cor de destaque", "ru": "Цвет акцента", "zh": "强调色", "ja": "アクセントカラー"
    },
    "pdf_export_theme_title": {
        "en": "PDF Export Theme", "es": "Tema de exportación de PDF", "fr": "Thème d'exportation PDF", "de": "PDF-Export-Design",
        "hi": "पीडीएफ निर्यात थीम", "pt": "Tema de exportação de PDF", "ru": "Тема экспорта PDF", "zh": "PDF 导出主题", "ja": "PDFエクスポートテーマ"
    },
    "pdf_export_theme_desc": {
        "en": "Set default theme for saved PDFs", "es": "Establecer tema predeterminado para los PDF guardados", "fr": "Définir le thème par défaut pour les PDF enregistrés", "de": "Standarddesign für gespeicherte PDFs festlegen",
        "hi": "सहेजे गए पीडीएफ के लिए डिफ़ॉल्ट थीम सेट करें", "pt": "Definir o tema padrão para os PDFs salvos", "ru": "Задать тему для сохраняемых PDF", "zh": "为保存的 PDF 设置默认主题", "ja": "保存されたPDFのデフォルトテーマを設定"
    },
    "notifications_title": {
        "en": "Notifications", "es": "Notificaciones", "fr": "Notifications", "de": "Benachrichtigungen",
        "hi": "सूचनाएं", "pt": "Notificações", "ru": "Уведомления", "zh": "通知", "ja": "通知"
    },
    "notifications_desc": {
        "en": "Enable push notifications", "es": "Habilitar notificaciones push", "fr": "Activer las notifications push", "de": "Push-Benachrichtigungen aktivieren",
        "hi": "पुश सूचनाएं सक्षम करें", "pt": "Ativar notificações push", "ru": "Включить push-уведомления", "zh": "启用推送通知", "ja": "プッシュ通知を有効にする"
    },
    "private_browsing_title": {
        "en": "Private Browsing", "es": "Navegación privada", "fr": "Navigation privée", "de": "Privates Surfen",
        "hi": "निजी ब्राउज़िंग", "pt": "Navegação privada", "ru": "Приватный просмотр", "zh": "无痕浏览", "ja": "プライベートブラウジング"
    },
    "private_browsing_desc": {
        "en": "Block trackers and cookies", "es": "Bloquear rastreadores y cookies", "fr": "Bloquer les traqueurs et cookies", "de": "Tracker und Cookies blockieren",
        "hi": "ट्रैकर्स और कुकीज़ को ब्लॉक करें", "pt": "Bloquear rastreadores e cookies", "ru": "Блокировать трекеры и файлы cookie", "zh": "阻止跟踪器和 Cookie", "ja": "トラッカーとクッキーをブロックする"
    },
    "native_player_title": {
        "en": "Native Video Player", "es": "Reproductor de video nativo", "fr": "Lecteur vidéo natif", "de": "Nativer Videoplayer",
        "hi": "मूल वीडियो प्लेयर", "pt": "Reprodutor de vídeo nativo", "ru": "Встроенный видеоплеер", "zh": "原生视频播放器", "ja": "ネイティブビデオプレーヤー"
    },
    "native_player_desc": {
        "en": "Open videos in Omni Player with download", "es": "Abrir videos en Omni Player con descarga", "fr": "Ouvrir les vidéos dans Omni Player avec téléchargement", "de": "Videos im Omni Player mit Download öffnen",
        "hi": "डाउनलोड के साथ ओमनी प्लेयर में वीडियो खोलें", "pt": "Abrir vídeos no Omni Player com download", "ru": "Открывать видео в плеере Omni с загрузкой", "zh": "在 Omni 播放器中打开视频并支持下载", "ja": "ダウンロード付きのOmni Playerでビデオを開く"
    },
    "ai_blocker_title": {
        "en": "AI Blocker", "es": "Bloqueador de IA", "fr": "Bloqueur d'IA", "de": "KI-Blocker",
        "hi": "एआई ब्लॉकर", "pt": "Bloqueador de IA", "ru": "Блокировщик ИИ", "zh": "AI 拦截器", "ja": "AIブロッカー"
    },
    "ai_blocker_desc": {
        "en": "Block generative search summaries completely", "es": "Bloquear resúmenes generativos de búsqueda por completo", "fr": "Bloquer complètement les résumés de recherche générative", "de": "Generative Suchzusammenfassungen komplett blockieren",
        "hi": "जेनरेटिव खोज सारांशों को पूरी तरह से ब्लॉक करें", "pt": "Bloquear resumos de pesquisa generativa completamente", "ru": "Полностью блокировать сводки генеративного поиска", "zh": "完全拦截生成式搜索摘要", "ja": "生成AIによる検索サマリーを完全にブロック"
    },
    "default_browser_title": {
        "en": "Default Browser", "es": "Navegador predeterminado", "fr": "Navigateur par défaut", "de": "Standardbrowser",
        "hi": "डिफ़ॉल्ट ब्राउज़र", "pt": "Navegador padrão", "ru": "Браузер по умолчанию", "zh": "默认浏览器", "ja": "デフォルトブラウザ"
    },
    "vpn_section": {
        "en": "WIREGUARD VPN", "es": "VPN WIREGUARD", "fr": "VPN WIREGUARD", "de": "WIREGUARD VPN",
        "hi": "वायरगार्ड वीपीएन", "pt": "VPN WIREGUARD", "ru": "WIREGUARD VPN", "zh": "WireGuard VPN", "ja": "WireGuard VPN"
    },
    "vpn_status_title": {
        "en": "VPN Tunnel Status", "es": "Estado del túnel VPN", "fr": "État du tunnel VPN", "de": "VPN-Tunnelstatus",
        "hi": "वीपीएन टनल स्थिति", "pt": "Status do túnel VPN", "ru": "Статус VPN-туннеля", "zh": "VPN 隧道状态", "ja": "VPNトンネルステータス"
    },
    "vpn_import_conf": {
        "en": "Import .conf", "es": "Importar .conf", "fr": "Importer .conf", "de": "Importieren .conf",
        "hi": "आयात .conf", "pt": "Importar .conf", "ru": "Импорт .conf", "zh": "导入 .conf", "ja": ".confをインポート"
    },
    "vpn_connect": {
        "en": "Connect", "es": "Conectar", "fr": "Se connecter", "de": "Verbinden",
        "hi": "कनेक्ट करें", "pt": "Conectar", "ru": "Подключить", "zh": "连接", "ja": "接続"
    },
    "vpn_disconnect": {
        "en": "Disconnect VPN", "es": "Desconectar VPN", "fr": "Déconnecter le VPN", "de": "VPN trennen",
        "hi": "वीपीएन डिस्कनेक्ट करें", "pt": "Desconectar VPN", "ru": "Отключить VPN", "zh": "断开 VPN", "ja": "VPNを切断"
    },
    "search_engine_section": {
        "en": "SEARCH ENGINE", "es": "MOTOR DE BÚSQUEDA", "fr": "MOTEUR DE RECHERCHE", "de": "SUCHMASCHINE",
        "hi": "खोज इंजन", "pt": "MOTOR DE BUSCA", "ru": "ПОИСКОВАЯ СИСТЕМА", "zh": "搜索引擎", "ja": "検索エンジン"
    },
    "search_engine_title": {
        "en": "Default Search Engine", "es": "Motor de búsqueda predeterminado", "fr": "Moteur de recherche par défaut", "de": "Standardsuchmaschine",
        "hi": "डिफ़ॉल्ट खोज इंजन", "pt": "Motor de busca padrão", "ru": "Поисковая система по умолчанию", "zh": "默认搜索引擎", "ja": "デフォルトの検索エンジン"
    },
    "google_accounts_section": {
        "en": "GOOGLE ACCOUNTS", "es": "CUENTAS DE GOOGLE", "fr": "COMPTES GOOGLE", "de": "GOOGLE-KONTEN",
        "hi": "गूगल खाते", "pt": "CONTAS DO GOOGLE", "ru": "АККАУНТЫ GOOGLE", "zh": "谷歌账户", "ja": "Google アカウント"
    },
    "google_sso_title": {
        "en": "Google Single Sign-On (SSO)", "es": "Inicio de sesión único de Google (SSO)", "fr": "Connexion unique Google (SSO)", "de": "Google Single Sign-On (SSO)",
        "hi": "गूगल सिंगल साइन-ऑन (SSO)", "pt": "Login único do Google (SSO)", "ru": "Вход через аккаунт Google (SSO)", "zh": "谷歌单点登录 (SSO)", "ja": "Googleシングルサインオン (SSO)"
    },
    "about_section": {
        "en": "ABOUT", "es": "ACERCA DE", "fr": "À PROPOS", "de": "ÜBER",
        "hi": "विवरण", "pt": "SOBRE", "ru": "О ПРИЛОЖЕНИИ", "zh": "关于", "ja": "情報"
    },
    "website_title": {
        "en": "Official Website", "es": "Sitio web oficial", "fr": "Site officiel", "de": "Offizielle Website",
        "hi": "आधिकारिक वेबसाइट", "pt": "Website oficial", "ru": "Официальный сайт", "zh": "官方网站", "ja": "公式サイト"
    },
    "privacy_policy_title": {
        "en": "Privacy Policy", "es": "Política de privacidad", "fr": "Politique de confidentialité", "de": "Datenschutzerklärung",
        "hi": "गोपनीयता नीति", "pt": "Política de privacidade", "ru": "Политика конфиденциальности", "zh": "隐私政策", "ja": "プライバシーポリシー"
    },
    "app_version_title": {
        "en": "App Version", "es": "Versión de la aplicación", "fr": "Version de l'application", "de": "App-Version",
        "hi": "ऐप संस्करण", "pt": "Versão do aplicativo", "ru": "Версия приложения", "zh": "应用版本", "ja": "アプリのバージョン"
    },
    "check_updates_title": {
        "en": "Check for updates", "es": "Buscar actualizaciones", "fr": "Rechercher des mises à jour", "de": "Nach Updates suchen",
        "hi": "अपडेट के लिए जांचें", "pt": "Buscar atualizações", "ru": "Проверить обновления", "zh": "检查更新", "ja": "アップデートを確認"
    },
    "checking_text": {
        "en": "Checking...", "es": "Comprobando...", "fr": "Vérification...", "de": "Überprüfen...",
        "hi": "जांच की जा रही है...", "pt": "Verificando...", "ru": "Проверка...", "zh": "正在检查...", "ja": "確認中..."
    },
    "check_now_text": {
        "en": "Check now", "es": "Comprobar ahora", "fr": "Vérifier maintenant", "de": "Jetzt prüfen",
        "hi": "अभी जांचें", "pt": "Verificar agora", "ru": "Проверить сейчас", "zh": "立即检查", "ja": "今すぐ確認"
    },
    "app_language_title": {
        "en": "App Language", "es": "Idioma de la aplicación", "fr": "Langue de l'application", "de": "App-Sprache",
        "hi": "ऐप की भाषा", "pt": "Idioma do aplicativo", "ru": "Язык приложения", "zh": "应用语言", "ja": "アプリの言語"
    },
    "app_language_desc": {
        "en": "Change the app's display language", "es": "Cambiar el idioma de visualización de la aplicación", "fr": "Changer la langue d'affichage de l'application", "de": "Anzeigesprache der App ändern",
        "hi": "ऐप की प्रदर्शन भाषा बदलें", "pt": "Alterar o idioma de exibição do aplicativo", "ru": "Изменить язык отображения приложения", "zh": "更改应用程序的显示语言", "ja": "アプリの表示言語を変更します"
    },
    "search_placeholder": {
        "en": "Search or type web address", "es": "Buscar o escribir dirección web", "fr": "Rechercher ou saisir une adresse web", "de": "Suchen oder Webadresse eingeben",
        "hi": "खोजें या वेब पता टाइप करें", "pt": "Pesquisar ou digitar endereço web", "ru": "Поиск или ввод веб-адреса", "zh": "搜索或输入网址", "ja": "検索またはウェブアドレスを入力"
    },
    "cancel_text": {
        "en": "Cancel", "es": "Cancelar", "fr": "Annuler", "de": "Abbrechen",
        "hi": "रद्द करें", "pt": "Cancelar", "ru": "Отмена", "zh": "取消", "ja": "キャンセル"
    },
    "play_text": {
        "en": "Play", "es": "Reproducir", "fr": "Lire", "de": "Abspielen",
        "hi": "चलाएं", "pt": "Reproduzir", "ru": "Воспроизвести", "zh": "播放", "ja": "再生"
    },
    "save_text": {
        "en": "Save", "es": "Guardar", "fr": "Enregistrer", "de": "Speichern",
        "hi": "सहेजें", "pt": "Salvar", "ru": "Сохранить", "zh": "保存", "ja": "保存"
    },
    "locker_text": {
        "en": "Locker", "es": "Locker", "fr": "Locker", "de": "Locker",
        "hi": "लॉकर", "pt": "Cofre", "ru": "Сейф", "zh": "保险箱", "ja": "ロッカー"
    },
    "translator_title": {
        "en": "On-Device Offline Translator", "es": "Traductor sin conexión local", "fr": "Traducteur hors ligne local", "de": "Lokaler Offline-Übersetzer",
        "hi": "ऑन-डिवाइस ऑफ़लाइन अनुवादक", "pt": "Tradutor offline no dispositivo", "ru": "Локальный оффлайн-переводчик", "zh": "设备端离线翻译", "ja": "オンデバイス オフライン翻訳"
    },
    "translator_desc": {
        "en": "Download translation packages offline. 100% private.", "es": "Descargar paquetes de traducción sin conexión. 100% privado.", "fr": "Téléchargez des packages de traduction hors ligne. 100% privé.", "de": "Übersetzungspakete offline herunterladen. 100% privat.",
        "hi": "ऑफ़लाइन अनुवाद पैकेज डाउनलोड करें। 100% निजी।", "pt": "Baixe pacotes de tradução offline. 100% privado.", "ru": "Скачивайте пакеты перевода оффлайн. 100% конфиденциально.", "zh": "下载离线翻译包。100% 私密保护。", "ja": "オフライン翻訳パッケージをダウンロードします。100％プライベート。"
    },
    "translator_placeholder": {
        "en": "Type text to translate...", "es": "Escribe texto a traducir...", "fr": "Saisissez le texte à traduire...", "de": "Text zum Übersetzen eingeben...",
        "hi": "अनुवाद करने के लिए पाठ लिखें...", "pt": "Digite o texto para traduzir...", "ru": "Введите текст для перевода...", "zh": "输入要翻译的文本...", "ja": "翻訳するテキストを入力してください..."
    },
    "translator_processing": {
        "en": "Processing offline model...", "es": "Procesando modelo local...", "fr": "Traitement du modèle hors ligne...", "de": "Lokales Modell wird verarbeitet...",
        "hi": "ऑफ़लाइन मॉडल संसाधित किया जा रहा है...", "pt": "Processando modelo offline...", "ru": "Обработка оффлайн-модели...", "zh": "正在处理离线模型...", "ja": "オフラインモデルを処理中..."
    },
    "translator_action": {
        "en": "Translate", "es": "Traducir", "fr": "Traduire", "de": "Übersetzen",
        "hi": "अनुवाद करें", "pt": "Traduzir", "ru": "Перевести", "zh": "翻译", "ja": "翻訳"
    },
    "close_text": {
        "en": "Close", "es": "Cerrar", "fr": "Fermer", "de": "Schließen",
        "hi": "बंद करें", "pt": "Fechar", "ru": "Закрыть", "zh": "关闭", "ja": "閉じる"
    },
    "clear_text": {
        "en": "Clear", "es": "Limpiar", "fr": "Effacer", "de": "Löschen",
        "hi": "साफ़ करें", "pt": "Limpar", "ru": "Очистить", "zh": "清除", "ja": "クリア"
    },
    "firefox_addons_desc": {
        "en": "Browse Official Firefox Add-ons Store", "es": "Explorar la tienda oficial de complementos de Firefox", "fr": "Parcourir la boutique officielle des modules Firefox", "de": "Offiziellen Firefox Add-ons Store durchsuchen",
        "hi": "आधिकारिक फ़ायरफ़ॉक्स ऐड-ऑन स्टोर ब्राउज़ करें", "pt": "Navegar na loja oficial de extensões do Firefox", "ru": "Официальный магазин дополнений Firefox", "zh": "浏览官方 Firefox 附加组件商店", "ja": "公式Firefoxアドオンストアを閲覧する"
    },
    "allow_text": {
        "en": "Allow", "es": "Permitir", "fr": "Autoriser", "de": "Zulassen",
        "hi": "अनुमति दें", "pt": "Permitir", "ru": "Разрешить", "zh": "允许", "ja": "許可"
    },
    "deny_text": {
        "en": "Deny", "es": "Denegar", "fr": "Refuser", "de": "Ablehnen",
        "hi": "अस्वीकार करें", "pt": "Recusar", "ru": "Запретить", "zh": "拒绝", "ja": "拒否"
    },
    "doc_scanner_title": {
        "en": "Doc Scanner", "es": "Escáner de documentos", "fr": "Scanner de documents", "de": "Dokumentenscanner",
        "hi": "दस्तावेज़ स्कैनर", "pt": "Escâner de documentos", "ru": "Сканер документов", "zh": "文档扫描", "ja": "ドキュメントスキャナー"
    },
    "qr_tools_title": {
        "en": "QR Tools", "es": "Herramientas QR", "fr": "Outils QR", "de": "QR-Tools",
        "hi": "क्यूआर टूल्स", "pt": "Ferramentas QR", "ru": "QR-инструменты", "zh": "QR 工具", "ja": "QRツール"
    },
    "safe_locker_title": {
        "en": "Safe Locker", "es": "Locker seguro", "fr": "Locker sécurisé", "de": "Sicherer Locker",
        "hi": "सुरक्षित लॉकर", "pt": "Cofre seguro", "ru": "Защищенный сейф", "zh": "安全保险箱", "ja": "セーフロッカー"
    },
    "translator_tool_title": {
        "en": "Translator", "es": "Traductor", "fr": "Traducteur", "de": "Übersetzer",
        "hi": "अनुवादक", "pt": "Tradutor", "ru": "Переводчик", "zh": "翻译官", "ja": "翻訳ツール"
    },
    "edit_page_title": {
        "en": "Edit Page", "es": "Editar página", "fr": "Modifier la page", "de": "Seite bearbeiten",
        "hi": "पृष्ठ संपादित करें", "pt": "Editar página", "ru": "Редактировать страницу", "zh": "编辑页面", "ja": "ページ編集"
    },
    "save_pdf_title": {
        "en": "Save PDF", "es": "Guardar PDF", "fr": "Enregistrer en PDF", "de": "Als PDF speichern",
        "hi": "पीडीएफ सहेजें", "pt": "Salvar PDF", "ru": "Сохранить PDF", "zh": "保存 PDF", "ja": "PDF保存"
    },
    "pin_web_app_title": {
        "en": "Pin Web App", "es": "Fijar aplicación web", "fr": "Épingler l'app web", "de": "Web-App anheften",
        "hi": "वेब ऐप पिन करें", "pt": "Fixar aplicativo web", "ru": "Закрепить веб-приложение", "zh": "固定网页应用", "ja": "ウェブアプリをピン留め"
    },
    "auto_scroll_title": {
        "en": "Auto-Scroll", "es": "Desplazamiento automático", "fr": "Défilement auto", "de": "Automatischer Bildlauf",
        "hi": "ऑटो-स्क्रॉल", "pt": "Rolagem automática", "ru": "Автоскроллинг", "zh": "自动滚动", "ja": "自動スクロール"
    },
    "quick_tools_title": {
        "en": "Quick Tools", "es": "Herramientas rápidas", "fr": "Outils rapides", "de": "Schnelltools",
        "hi": "त्वरित उपकरण", "pt": "Ferramentas rápidas", "ru": "Быстрые инструменты", "zh": "便捷工具", "ja": "クイックツール"
    },
    "quick_tools_desc": {
        "en": "Access offline ML-powered scanner, translation utilities, secure encrypted locker, and system tools.",
        "es": "Acceda al escáner sin conexión impulsado por ML, utilidades de traducción, casillero cifrado seguro y herramientas del sistema.",
        "fr": "Accédez au scanner hors ligne basé sur le ML, utilitaires de traduction, casier crypté sécurisé et outils système.",
        "de": "Greifen Sie auf den Offline-ML-Scanner, Übersetzungsprogramme, einen verschlüsselten Safe und Systemtools zu.",
        "hi": "ऑफ़लाइन एमएल-संचालित स्कैनर, अनुवाद उपयोगिताओं, सुरक्षित एन्क्रिप्टेड लॉकर और सिस्टम टूल तक पहुंचें।",
        "pt": "Acesse escâner offline alimentado por ML, utilitários de tradução, cofre seguro criptografado e ferramentas do sistema.",
        "ru": "Доступ к оффлайн-сканеру на базе машинного обучения, функциям перевода, защищенному зашифрованному сейфу и системным инструментам.",
        "zh": "访问离线机器学习扫描仪、翻译工具、安全加密保险箱和系统工具。",
        "ja": "オフライン機械学習スキャナー、翻訳ユーティリティ、安全な暗号化ロッカー、システムツールにアクセスします。"
    },
    "bookmarks_title": {
        "en": "Bookmarks", "es": "Marcadores", "fr": "Favoris", "de": "Lesezeichen",
        "hi": "बुकमार्क", "pt": "Favoritos", "ru": "Закладки", "zh": "书签", "ja": "ブックマーク"
    },
    "bookmarks_clear_all": {
        "en": "Clear all", "es": "Limpiar todo", "fr": "Tout effacer", "de": "Alle löschen",
        "hi": "सभी साफ़ करें", "pt": "Limpar tudo", "ru": "Очистить все", "zh": "清除全部", "ja": "すべてクリア"
    },
    "bookmarks_search_placeholder": {
        "en": "Search bookmarks", "es": "Buscar marcadores", "fr": "Rechercher des favoris", "de": "Lesezeichen durchsuchen",
        "hi": "बुकमार्क खोजें", "pt": "Pesquisar favoritos", "ru": "Поиск по закладкам", "zh": "搜索书签", "ja": "ブックマークを検索"
    },
    "bookmarks_empty": {
        "en": "No bookmarks found", "es": "No se encontraron marcadores", "fr": "Aucun favori trouvé", "de": "Keine Lesezeichen gefunden",
        "hi": "कोई बुकमार्क नहीं मिला", "pt": "Nenhum favorito encontrado", "ru": "Закладки не найдены", "zh": "未找到书签", "ja": "ブックマークが見つかりません"
    },
    "bookmarks_clear_confirm": {
        "en": "Are you sure you want to clear all bookmarks?", "es": "¿Está seguro de que desea borrar todos los marcadores?", "fr": "Voulez-vous vraiment effacer tous les favoris ?", "de": "Möchten Sie wirklich alle Lesezeichen löschen?",
        "hi": "क्या आप वाकई सभी बुकमार्क साफ़ करना चाहते हैं?", "pt": "Tem certeza de que deseja limpar todos os favoritos?", "ru": "Вы уверены, что хотите удалить все закладки?", "zh": "您确定要清除所有书签吗？", "ja": "本当にすべてのブックマークをクリアしますか？"
    },
    "history_title": {
        "en": "History", "es": "Historial", "fr": "Historique", "de": "Verlauf",
        "hi": "इतिहास", "pt": "Histórico", "ru": "История", "zh": "历史记录", "ja": "履歴"
    },
    "history_clear_all": {
        "en": "Clear all", "es": "Limpiar todo", "fr": "Tout effacer", "de": "Alle löschen",
        "hi": "सभी साफ़ करें", "pt": "Limpar tudo", "ru": "Очистить все", "zh": "清除全部", "ja": "すべてクリア"
    },
    "history_search_placeholder": {
        "en": "Search history", "es": "Buscar en el historial", "fr": "Rechercher dans l'historique", "de": "Verlauf durchsuchen",
        "hi": "इतिहास खोजें", "pt": "Pesquisar histórico", "ru": "Поиск по истории", "zh": "搜索历史", "ja": "履歴を検索"
    },
    "history_empty": {
        "en": "No history found", "es": "No se encontró historial", "fr": "Aucun historique trouvé", "de": "Kein Verlauf gefunden",
        "hi": "कोई इतिहास नहीं मिला", "pt": "Nenhum histórico encontrado", "ru": "История не найдена", "zh": "未找到历史记录", "ja": "履歴が見つかりません"
    },
    "history_clear_confirm": {
        "en": "Are you sure you want to clear all history?", "es": "¿Está seguro de que desea borrar todo el historial?", "fr": "Voulez-vous vraiment effacer tout l'historique ?", "de": "Möchten Sie wirklich den gesamten Verlauf löschen?",
        "hi": "क्या आप वाकई सभी इतिहास साफ़ करना चाहते हैं?", "pt": "Tem certeza de que deseja limpar todo o histórico?", "ru": "Вы уверены, что хотите очистить всю историю?", "zh": "您确定要清除所有历史记录吗？", "ja": "本当にすべての履歴をクリアしますか？"
    },
    "downloads_title": {
        "en": "Downloads", "es": "Descargas", "fr": "Téléchargements", "de": "Downloads",
        "hi": "डाउनलोड", "pt": "Downloads", "ru": "Загрузки", "zh": "下载", "ja": "ダウンロード"
    },
    "downloads_active_tab": {
        "en": "Active", "es": "Activas", "fr": "Actifs", "de": "Aktiv",
        "hi": "सक्रिय", "pt": "Ativos", "ru": "Активные", "zh": "活动中", "ja": "アクティブ"
    },
    "downloads_completed_tab": {
        "en": "Completed", "es": "Completadas", "fr": "Terminés", "de": "Abgeschlossen",
        "hi": "पूर्ण", "pt": "Concluídos", "ru": "Завершенные", "zh": "已完成", "ja": "完了"
    },
    "downloads_empty": {
        "en": "No downloads found", "es": "No se encontraron descargas", "fr": "Aucun téléchargement trouvé", "de": "Keine Downloads gefunden",
        "hi": "कोई डाउनलोड नहीं मिला", "pt": "Nenhum download encontrado", "ru": "Загрузки не найдены", "zh": "没有找到下载内容", "ja": "ダウンロードが見つかりません"
    },
    "pip_mode": {
        "en": "PiP", "es": "PiP", "fr": "PiP", "de": "PiP",
        "hi": "पीआईपी", "pt": "PiP", "ru": "Картинка в картинке", "zh": "画中画", "ja": "画中画"
    },
    "locker_pin_prompt": {
        "en": "Enter PIN to access your secure files", "es": "Introduzca el PIN para acceder a sus archivos seguros", "fr": "Saisissez le code PIN pour accéder à vos fichiers sécurisés", "de": "PIN eingeben, um auf sichere Dateien zuzugreifen",
        "hi": "अपनी सुरक्षित फ़ाइलों तक पहुँचने के लिए पिन दर्ज करें", "pt": "Digite o PIN para acessar seus arquivos seguros", "ru": "Введите PIN для доступа к защищенным файлам", "zh": "输入 PIN 码以访问您的安全文件", "ja": "安全なファイルにアクセスするためにPINを入力してください"
    },
    "locker_pin_incorrect": {
        "en": "Incorrect PIN", "es": "PIN incorrecto", "fr": "Code PIN incorrect", "de": "Falsche PIN",
        "hi": "गलत पिन", "pt": "PIN incorreto", "ru": "Неверный PIN", "zh": "PIN 码不正确", "ja": "PINコードが正しくありません"
    },
    "locker_create_pin": {
        "en": "Create PIN", "es": "Crear PIN", "fr": "Créer un code PIN", "de": "PIN erstellen",
        "hi": "पिन बनाएं", "pt": "Criar PIN", "ru": "Создать PIN", "zh": "创建 PIN 码", "ja": "PINを作成"
    },
    "locker_confirm_pin": {
        "en": "Confirm PIN", "es": "Confirmar PIN", "fr": "Confirmer le code PIN", "de": "PIN bestätigen",
        "hi": "पिन की पुष्टि करें", "pt": "Confirmar PIN", "ru": "Подтвердить PIN", "zh": "确认 PIN 码", "ja": "PINを確認"
    },
    "locker_empty": {
        "en": "No secure files found", "es": "No se encontraron archivos seguros", "fr": "Aucun fichier sécurisé trouvé", "de": "Keine sicheren Dateien gefunden",
        "hi": "कोई सुरक्षित फ़ाइल नहीं मिली", "pt": "Nenhum arquivo seguro encontrado", "ru": "Защищенные файлы не найдены", "zh": "未找到安全文件", "ja": "安全なファイルが見つかりません"
    },
    "qr_scan_tab": {
        "en": "Scan QR Code", "es": "Escanear código QR", "fr": "Scanner un code QR", "de": "QR-Code scannen",
        "hi": "क्यूआर कोड स्कैन करें", "pt": "Escanear código QR", "ru": "Сканировать QR-код", "zh": "扫描二维码", "ja": "QRコードをスキャン"
    },
    "qr_generate_tab": {
        "en": "Generate QR Code", "es": "Generar código QR", "fr": "Générer un code QR", "de": "QR-Code generieren",
        "hi": "क्यूआर कोड जेनरेट करें", "pt": "Gerar código QR", "ru": "Создать QR-код", "zh": "生成二维码", "ja": "QRコードを生成"
    },
    "qr_scan_result": {
        "en": "Scan Result", "es": "Resultado del escaneo", "fr": "Résultat du scan", "de": "Scan-Ergebnis",
        "hi": "स्कैन परिणाम", "pt": "Resultado do escaneamento", "ru": "Результат сканирования", "zh": "扫描结果", "ja": "スキャン結果"
    },
    "qr_generate_placeholder": {
        "en": "Type URL/Text to generate QR Code", "es": "Escriba URL/Texto para generar código QR", "fr": "Saisissez l'URL/le texte pour générer un code QR", "de": "URL/Text eingeben, um QR-Code zu generieren",
        "hi": "क्यूआर कोड जेनरेट करने के लिए यूआरएल/टेक्स्ट टाइप करें", "pt": "Digite URL/Texto para gerar código QR", "ru": "Введите URL/текст для создания QR-кода", "zh": "输入网址/文本以生成二维码", "ja": "QRコードを生成するためのURL /テキストを入力してください"
    },
    "qr_save_gallery": {
        "en": "Save QR Code to Gallery", "es": "Guardar código QR en la galería", "fr": "Enregistrer le code QR dans la galerie", "de": "QR-Code in der Galerie speichern",
        "hi": "गैलरी में क्यूआर कोड सहेजें", "pt": "Salvar código QR na galeria", "ru": "Сохранить QR-код в галерею", "zh": "保存二维码到相册", "ja": "QRコードをギャラリーに保存"
    },
    "qr_share": {
        "en": "Share QR Code", "es": "Compartir código QR", "fr": "Partager le code QR", "de": "QR-Code teilen",
        "hi": "क्यूआर कोड साझा करें", "pt": "Compartilhar código QR", "ru": "Поделиться QR-кодом", "zh": "分享二维码", "ja": "QRコードを共有"
    },
    "doc_scanner_frame_prompt": {
        "en": "Position the document inside the frame", "es": "Coloque el documento dentro del marco", "fr": "Placez le document dans le cadre", "de": "Positionieren Sie das Dokument im Rahmen",
        "hi": "दस्तावेज़ को फ्रेम के अंदर रखें", "pt": "Posicione o documento dentro da moldura", "ru": "Поместите документ в рамку", "zh": "将文档置于框内", "ja": "ドキュメントをフレーム内に配置してください"
    },
    "doc_scanner_capturing": {
        "en": "Capturing...", "es": "Capturando...", "fr": "Capture...", "de": "Erfassen...",
        "hi": "कैप्चर किया जा रहा है...", "pt": "Capturando...", "ru": "Съемка...", "zh": "正在拍摄...", "ja": "キャプチャ中..."
    },
    "doc_scanner_review": {
        "en": "Review scanned page", "es": "Revisar página escaneada", "fr": "Vérifier la page scannée", "de": "Gescannte Seite prüfen",
        "hi": "स्कैन किए गए पृष्ठ की समीक्षा करें", "pt": "Revisar página escaneada", "ru": "Проверка отсканированной страницы", "zh": "预览扫描页面", "ja": "スキャンされたページを確認"
    },
    "doc_scanner_save_pdf": {
        "en": "Save PDF", "es": "Guardar PDF", "fr": "Enregistrer en PDF", "de": "Als PDF speichern",
        "hi": "पीडीएफ सहेजें", "pt": "Salvar PDF", "ru": "Сохранить PDF", "zh": "保存 PDF", "ja": "PDF保存"
    },
    "system_default": {
        "en": "System Default", "es": "Predeterminado del sistema", "fr": "Par défaut du système", "de": "Systemstandard",
        "hi": "सिस्टम डिफ़ॉल्ट", "pt": "Padrão do sistema", "ru": "Системное значение по умолчанию", "zh": "系统默认", "ja": "システムデフォルト"
    },
    "dark_theme": {
        "en": "Dark Theme", "es": "Tema oscuro", "fr": "Thème sombre", "de": "Dunkles Design",
        "hi": "डार्क थीम", "pt": "Tema escuro", "ru": "Темная тема", "zh": "深色主题", "ja": "ダークテーマ"
    },
    "light_theme": {
        "en": "Light Theme", "es": "Tema claro", "fr": "Thème clair", "de": "Helles Design",
        "hi": "लाइट थीम", "pt": "Tema claro", "ru": "Светлая тема", "zh": "浅色主题", "ja": "ライトテーマ"
    },
    "default_browser_active": {
        "en": "Set as default browser (Active)", "es": "Establecido como navegador predeterminado (Activo)", "fr": "Défini comme navigateur par défaut (Actif)", "de": "Als Standardbrowser festgelegt (Aktiv)",
        "hi": "डिफ़ॉल्ट ब्राउज़र के रूप में सेट (सक्रिय)", "pt": "Definido como navegador padrão (Ativo)", "ru": "Установлен как браузер по умолчанию (Активно)", "zh": "已设为默认浏览器 (处于活动状态)", "ja": "デフォルトブラウザに設定されています (有効)"
    },
    "default_browser_inactive": {
        "en": "Not set as default. Tap to set.", "es": "No establecido como predeterminado. Toque para establecer.", "fr": "Non défini par défaut. Appuyez pour définir.", "de": "Nicht als Standard festgelegt. Zum Festlegen tippen.",
        "hi": "डिफ़ॉल्ट के रूप में सेट नहीं है। सेट करने के लिए टैप करें।", "pt": "Não definido como padrão. Toque para definir.", "ru": "Не установлен по умолчанию. Нажмите, чтобы установить.", "zh": "未设为默认值。点击进行设置。", "ja": "デフォルトに設定されていません。タップして設定します。"
    },
    "custom_query_template": {
        "en": "Custom Query Template URL", "es": "URL de plantilla de consulta personalizada", "fr": "URL du modèle de requête personnalisée", "de": "Benutzerdefinierte Abfrage-Vorlagen-URL",
        "hi": "कस्टम क्वेरी टेम्प्लेट URL", "pt": "URL do modelo de consulta personalizada", "ru": "Пользовательский URL-шаблон запроса", "zh": "自定义查询模板 URL", "ja": "カスタムクエリテンプレートURL"
    },
    "custom_query_placeholder_desc": {
        "en": "Use %s as a placeholder for the search query.", "es": "Use %s como marcador de posición para la consulta de búsqueda.", "fr": "Utilisez %s comme espace réservé pour la requête de recherche.", "de": "Verwenden Sie %s als Platzhalter für die Suchabfrage.",
        "hi": "खोज क्वेरी के लिए प्लेसहोल्डर के रूप में %s का उपयोग करें।", "pt": "Use %s como espaço reservado para a consulta de pesquisa.", "ru": "Используйте %s в качестве заполнителя для поискового запроса.", "zh": "使用 %s 作为搜索查询 of 占位符。", "ja": "検索クエリのプレースホルダーとして %s を使用します。"
    },
    "vpn_status_connected": {
        "en": "Connected", "es": "Conectado", "fr": "Connecté", "de": "Verbunden",
        "hi": "कनेक्टेड", "pt": "Conectado", "ru": "Подключено", "zh": "已连接", "ja": "接続済み"
    },
    "vpn_status_connecting": {
        "en": "Connecting...", "es": "Conectando...", "fr": "Connexion...", "de": "Verbindung wird hergestellt...",
        "hi": "कनेक्ट किया जा रहा है...", "pt": "Conectando...", "ru": "Подключение...", "zh": "正在连接...", "ja": "接続中..."
    },
    "vpn_status_disconnected": {
        "en": "Disconnected", "es": "Desconectado", "fr": "Déconnecté", "de": "Getrennt",
        "hi": "डिस्कनेक्टेड", "pt": "Desconectado", "ru": "Отключено", "zh": "已断开", "ja": "切断"
    },
    "vpn_status_error_prefix": {
        "en": "Error: %1$s", "es": "Error: %1$s", "fr": "Erreur : %1$s", "de": "Fehler: %1$s",
        "hi": "त्रुटि: %1$s", "pt": "Erro: %1$s", "ru": "Ошибка: %1$s", "zh": "错误: %1$s", "ja": "エラー: %1$s"
    },
    "sign_out_title": {
        "en": "Sign Out", "es": "Cerrar sesión", "fr": "Se déconnecter", "de": "Abmelden",
        "hi": "साइन आउट", "pt": "Sair", "ru": "Выйти", "zh": "退出登录", "ja": "サインアウト"
    },
    "sign_out_confirm_desc": {
        "en": "Are you sure you want to sign out of your Google account?", "es": "¿Está seguro de que desea cerrar la sesión de su cuenta de Google?", "fr": "Voulez-vous vraiment vous déconnecter de votre compte Google ?", "de": "Sind Sie sicher, dass Sie sich von Ihrem Google-Konto abmelden möchten?",
        "hi": "क्या आप वाकई अपने Google खाते से साइन आउट करना चाहते हैं?", "pt": "Tem certeza de que deseja sair da sua conta do Google?", "ru": "Вы действительно хотите выйти из своего аккаунта Google?", "zh": "您确定要退出您的 Google 账户吗？", "ja": "本当にGoogleアカウントからサインアウトしますか？"
    },
    "update_dialog_later": {
        "en": "Later", "es": "Más tarde", "fr": "Plus tard", "de": "Später",
        "hi": "बाद में", "pt": "Mais tarde", "ru": "Позже", "zh": "稍后", "ja": "後で"
    },
    "update_dialog_btn": {
        "en": "Update", "es": "Actualizar", "fr": "Mettre à jour", "de": "Aktualisieren",
        "hi": "अपडेट करें", "pt": "Atualizar", "ru": "Обновить", "zh": "更新", "ja": "アップデート"
    },
    "update_dialog_title": {
        "en": "Update Available", "es": "Actualización disponible", "fr": "Mise à jour disponible", "de": "Update verfügbar",
        "hi": "अपडेट उपलब्ध है", "pt": "Atualização disponível", "ru": "Доступно обновление", "zh": "有可用更新", "ja": "アップデートがあります"
    },
    "update_dialog_desc": {
        "en": "A new version (%1$s) of Omni Browser is available on the Play Store. Would you like to update now?", "es": "Una nueva versión (%1$s) de Omni Browser está disponible en Play Store. ¿Desea actualizar ahora?", "fr": "Une nouvelle version (%1$s) d'Omni Browser est disponible sur le Play Store. Voulez-vous mettre à jour maintenant ?", "de": "Eine neue Version (%1$s) von Omni Browser ist im Play Store verfügbar. Möchten Sie jetzt aktualisieren?",
        "hi": "प्ले स्टोर पर Omni Browser का एक नया संस्करण (%1$s) उपलब्ध है। क्या आप अभी अपडेट करना चाहेंगे?", "pt": "Uma nova versão (%1$s) do Omni Browser está disponível na Play Store. Deseja atualizar agora?", "ru": "Новая версия (%1$s) Omni Browser доступна в Play Store. Хотите обновить сейчас?", "zh": "Play 商店中已有新版本 (%1$s) 的 Omni 浏览器。您要现在更新吗？", "ja": "Playストアで新しいバージョン（%1$s）のOmniブラウザが利用可能です。今すぐアップデートしますか？"
    },
    "update_failed_title": {
        "en": "Update Check Failed", "es": "Error al buscar actualizaciones", "fr": "Échec de la recherche de mise à jour", "de": "Fehler beim Suchen nach Updates",
        "hi": "अपडेट जांच विफल रही", "pt": "Falha na verificação de atualização", "ru": "Не удалось проверить обновления", "zh": "更新检查失败", "ja": "アップデートの確認に失敗しました"
    },
    "update_failed_desc": {
        "en": "Unable to connect to the update server (%1$s). Would you like to check the Play Store manually?", "es": "No se puede conectar al servidor de actualización (%1$s). ¿Desea consultar Play Store manualmente?", "fr": "Impossible de se connecter au serveur de mise à jour (%1$s). Voulez-vous vérifier le Play Store manuellement ?", "de": "Verbindung zum Update-Server (%1$s) nicht möglich. Möchten Sie den Play Store manuell überprüfen?",
        "hi": "अपडेट सर्वर (%1$s) से कनेक्ट करने में असमर्थ। क्या आप मैन्युअल रूप से Play स्टोर की जांच करना चाहेंगे?", "pt": "Não foi possível conectar ao servidor de atualização (%1$s). Deseja verificar a Play Store manualmente?", "ru": "Не удалось подключиться к серверу обновлений (%1$s). Хотите проверить Play Store вручную?", "zh": "无法连接到更新服务器 (%1$s)。您想手动检查 Play 商店吗？", "ja": "アップデートサーバー（%1$s）に接続できません。手動でPlayストアを確認しますか？"
    },
    "update_dialog_open_store": {
        "en": "Open Play Store", "es": "Abrir Play Store", "fr": "Ouvrir le Play Store", "de": "Play Store öffnen",
        "hi": "प्ले स्टोर खोलें", "pt": "Abrir Play Store", "ru": "Открыть Play Store", "zh": "打开 Play 商店", "ja": "Playストアを開く"
    },
    "support_github": {
        "en": "GitHub Support (RebelRoot)", "es": "Soporte de GitHub (RebelRoot)", "fr": "Support GitHub (RebelRoot)", "de": "GitHub-Support (RebelRoot)",
        "hi": "GitHub समर्थन (RebelRoot)", "pt": "Suporte do GitHub (RebelRoot)", "ru": "Поддержка GitHub (RebelRoot)", "zh": "GitHub 支持 (RebelRoot)", "ja": "GitHubサポート (RebelRoot)"
    },
    "website_omnibrowser": {
        "en": "OmniBrowser Website", "es": "Sitio web de OmniBrowser", "fr": "Site web OmniBrowser", "de": "OmniBrowser-Website",
        "hi": "OmniBrowser वेबसाइट", "pt": "Site do OmniBrowser", "ru": "Сайт OmniBrowser", "zh": "OmniBrowser 网站", "ja": "OmniBrowser ウェブサイト"
    },
    "update_no_update": {
        "en": "Omni Browser is up to date (v%1$s)", "es": "Omni Browser está actualizado (v%1$s)", "fr": "Omni Browser est à jour (v%1$s)", "de": "Omni Browser ist auf dem neuesten Stand (v%1$s)",
        "hi": "Omni Browser अद्यतित है (v%1$s)", "pt": "O Omni Browser está atualizado (v%1$s)", "ru": "Omni Browser обновлен до последней версии (v%1$s)", "zh": "Omni 浏览器已是最新版本 (v%1$s)", "ja": "Omniブラウザは最新です (v%1$s)"
    },
    "locker_cat_images": {
        "en": "Images", "es": "Imágenes", "fr": "Images", "de": "Bilder",
        "hi": "फ़ोटो", "pt": "Imagens", "ru": "Изображения", "zh": "图片", "ja": "画像"
    },
    "locker_cat_videos": {
        "en": "Videos", "es": "Videos", "fr": "Vidéos", "de": "Videos",
        "hi": "वीडियो", "pt": "Vídeos", "ru": "Видео", "zh": "视频", "ja": "動画"
    },
    "locker_cat_docs": {
        "en": "Docs", "es": "Documentos", "fr": "Documents", "de": "Dokumente",
        "hi": "दस्तावेज़", "pt": "Documentos", "ru": "Документы", "zh": "文档", "ja": "ドキュメント"
    },
    "locker_cat_epub": {
        "en": "Epub", "es": "Epub", "fr": "Epub", "de": "Epub",
        "hi": "ई-बुक", "pt": "Epub", "ru": "Epub", "zh": "电子书", "ja": "Epub"
    },
    "locker_cat_txt": {
        "en": "Txt", "es": "Texto", "fr": "Texte", "de": "Text",
        "hi": "टेक्स्ट", "pt": "Texto", "ru": "Текст", "zh": "文本", "ja": "テキスト"
    },
    "locker_cat_others": {
        "en": "Others", "es": "Otros", "fr": "Autres", "de": "Andere",
        "hi": "अन्य", "pt": "Outros", "ru": "Другие", "zh": "其他", "ja": "その他"
    },
    "locker_folders_title": {
        "en": "Secure Folders", "es": "Carpetas seguras", "fr": "Dossiers sécurisés", "de": "Sichere Ordner",
        "hi": "सुरक्षित फ़ोल्डर", "pt": "Pastas seguras", "ru": "Защищенные папки", "zh": "安全文件夹", "ja": "安全なフォルダー"
    },
    "locker_items_count": {
        "en": "%1$d items", "es": "%1$d elementos", "fr": "%1$d éléments", "de": "%1$d Elemente",
        "hi": "%1$d आइटम", "pt": "%1$d itens", "ru": "%1$d элементов", "zh": "%1$d 个项目", "ja": "%1$d 個のアイテム"
    },
    "locker_folder_detail_title": {
        "en": "%1$s Folder", "es": "Carpeta %1$s", "fr": "Dossier %1$s", "de": "Ordner %1$s",
        "hi": "%1$s फ़ोल्डर", "pt": "Pasta %1$s", "ru": "Папка %1$s", "zh": "%1$s 文件夹", "ja": "%1$s フォルダー"
    },
    "locker_empty": {
        "en": "Folder is empty", "es": "La carpeta está vacía", "fr": "Le dossier est vide", "de": "Ordner ist leer",
        "hi": "फ़ोल्डर खाली है", "pt": "A pasta está vazia", "ru": "Папка пуста", "zh": "文件夹为空", "ja": "フォルダーは空です"
    },
    "locker_setup_pin_title": {
        "en": "Setup In-App PIN", "es": "Configurar PIN de la aplicación", "fr": "Configurer le code PIN de l'application", "de": "In-App-PIN einrichten",
        "hi": "इन-ऐप पिन सेट करें", "pt": "Configurar PIN do aplicativo", "ru": "Nastrojka PIN-koda prilozhenija", "zh": "设置应用内 PIN 码", "ja": "アプリ内PINの設定"
    },
    "locker_enter_pin_title": {
        "en": "Enter In-App PIN", "es": "Introducir PIN de la aplicación", "fr": "Saisir le code PIN de l'application", "de": "In-App-PIN eingeben",
        "hi": "इन-ऐप पिन दर्ज करें", "pt": "Inserir PIN do aplicativo", "ru": "Vvedite PIN-kod prilozhenija", "zh": "输入应用内 PIN 码", "ja": "アプリ内PINの入力"
    },
    "locker_pin_length_error": {
        "en": "PIN must be at least 4 digits", "es": "El PIN debe tener al menos 4 dígitos", "fr": "Le code PIN doit comporter au moins 4 chiffres", "de": "Die PIN muss mindestens 4 Ziffern lang sein",
        "hi": "पिन कम से कम 4 अंकों का होना चाहिए", "pt": "O PIN deve ter pelo menos 4 dígitos", "ru": "PIN-kod dolzhen sostojat' minimum iz 4 cifr", "zh": "PIN 码必须至少为 4 位数字", "ja": "PINは少なくとも4桁である必要があります"
    },
    "locker_pin_incorrect": {
        "en": "Incorrect PIN", "es": "PIN incorrecto", "fr": "Code PIN incorrect", "de": "Falsche PIN",
        "hi": "गलत पिन", "pt": "PIN incorreto", "ru": "Nevernyj PIN-kod", "zh": "PIN 码错误", "ja": "PINコードが正しくありません"
    },
    "locker_save_unlock": {
        "en": "Save & Unlock", "es": "Guardar y desbloquear", "fr": "Enregistrer et déverrouiller", "de": "Speichern & Entsperren",
        "hi": "सहेजें और अनलॉक करें", "pt": "Salvar e desbloquear", "ru": "Sohranit' i razblokirovat'", "zh": "保存并解锁", "ja": "保存して解除"
    },
    "locker_unlock_vault": {
        "en": "Unlock Vault", "es": "Desbloquear bóveda", "fr": "Déverrouiller le coffre-fort", "de": "Tresor entsperren",
        "hi": "तिजोरी खोलें", "pt": "Desbloquear cofre", "ru": "Razblokirovat' sejf", "zh": "解锁保险箱", "ja": "保管庫を解除"
    },
    "locker_import_success": {
        "en": "Imported %1$d files successfully", "es": "%1$d archivos importados con éxito", "fr": "%1$d fichiers importés avec succès", "de": "%1$d Dateien erfolgreich importiert",
        "hi": "%1$d फ़ाइलें सफलतापूर्वक आयात की गईं", "pt": "%1$d arquivos importados com sucesso", "ru": "Uspeshno importirovano fajlov: %1$d", "zh": "成功导入 %1$d 个文件", "ja": "%1$d 件のファイルをインポートしました"
    },
    "locker_import_failed": {
        "en": "Failed to import files", "es": "Error al importar archivos", "fr": "Échec de l'importation des fichiers", "de": "Fehler beim Importieren von Dateien",
        "hi": "फ़ाइलें आयात करने में विफल", "pt": "Falha ao importar arquivos", "ru": "Ne udalos' importirovat' fajly", "zh": "导入文件失败", "ja": "ファイルのインポートに失敗しました"
    },
    "locker_open_failed": {
        "en": "No app found to open this file type.", "es": "No se encontró ninguna aplicación para abrir este tipo de archivo.", "fr": "Aucune application trouvée pour ouvrir ce type de fichier.", "de": "Keine App zum Öffnen dieses Dateityps gefunden.",
        "hi": "इस प्रकार की फ़ाइल खोलने के लिए कोई ऐप नहीं मिला।", "pt": "Nenhum aplicativo encontrado para abrir este tipo de arquivo.", "ru": "Ne najdeno prilozhenie dlja otkrytija etogo tipa fajlov.", "zh": "未找到打开此文件类型的应用。", "ja": "このファイル形式を開くアプリが見つかりません।"
    },
    "locker_vault_locked": {
        "en": "Vault Locked", "es": "Bóveda bloqueada", "fr": "Coffre-fort verrouillé", "de": "Tresor gesperrt",
        "hi": "तिजोरी बंद है", "pt": "Cofre bloqueado", "ru": "Sejf zablokirovan", "zh": "保险箱已锁定", "ja": "保管庫がロックされています"
    },
    "locker_auth_desc": {
        "en": "Authenticate using fingerprint or device credential to reveal files.", "es": "Autentíquese mediante huella digital o credencial del dispositivo para revelar los archivos.", "fr": "Authentifiez-vous à l'aide de l'empreinte digitale ou des identifiants de l'appareil pour révéler les fichiers.", "de": "Authentifizieren Sie sich per Fingerabdruck oder Geräte-Anmeldeinformationen, um Dateien anzuzeigen.",
        "hi": "फ़ाइलों को देखने के लिए फिंगरप्रिंट या डिवाइस क्रेडेंशियल का उपयोग करके प्रमाणित करें।", "pt": "Autentique-se usando impressão digital ou credencial do dispositivo para revelar os arquivos.", "ru": "Podtverdite lichnost' s pomoshh'ju otpechatka pal'ca ili parolja ustrojstva, chtoby uvidet' fajly.", "zh": "使用指纹或设备凭据进行身份验证以显示文件。", "ja": "ファイルを公開するには、指紋またはデバイスの資格情報を使用して認証します。"
    },
    "locker_setup_pin": {
        "en": "Setup In-App PIN", "es": "Configurar PIN de la aplicación", "fr": "Configurer le code PIN de l'application", "de": "In-App-PIN einrichten",
        "hi": "इन-ऐप पिन सेट करें", "pt": "Configurar PIN do aplicativo", "ru": "Nastroit' PIN-kod prilozhenija", "zh": "设置应用内 PIN 码", "ja": "アプリ内PINの設定"
    },
    "locker_unlock_pin": {
        "en": "Unlock with In-App PIN", "es": "Desbloquear con PIN de la aplicación", "fr": "Déverrouiller avec le code PIN de l'application", "de": "Mit In-App-PIN entsperren",
        "hi": "इन-ऐप पिन से अनलॉक करें", "pt": "Desbloquear com PIN do aplicativo", "ru": "Razblokirovat' s pomoshh'ju PIN-koda prilozhenija", "zh": "使用应用内 PIN 码解锁", "ja": "アプリ内PINで解除"
    },
    "qr_share": {
        "en": "Share QR Code", "es": "Compartir código QR", "fr": "Partager le code QR", "de": "QR-Code teilen",
        "hi": "क्यूआर कोड साझा करें", "pt": "Compartilhar código QR", "ru": "Podelit'sja QR-kodom", "zh": "分享二维码", "ja": "QRコードを共有"
    },
    "qr_saved_toast": {
        "en": "QR saved to gallery", "es": "QR guardado en la galería", "fr": "QR enregistré dans la galerie", "de": "QR in der Galerie gespeichert",
        "hi": "क्यूआर गैलरी में सहेजा गया", "pt": "QR salvo na galeria", "ru": "QR-kod sohranen v galereju", "zh": "二维码已保存至相册", "ja": "QRコードをギャラリーに保存しました"
    },
    "qr_tools_screen_title": {
        "en": "QR & Barcode Tools", "es": "Herramientas de código QR y de barras", "fr": "Outils de code QR & à barres", "de": "QR- & Barcode-Tools",
        "hi": "क्यूआर और बारकोड टूल्स", "pt": "Ferramentas de código QR e de barras", "ru": "Instrumenty dlja QR- i shtrih-kodov", "zh": "二维码和条形码工具", "ja": "QR・バーコードツール"
    },
    "qr_scan_tab": {
        "en": "Scan Code", "es": "Escanear código", "fr": "Scanner le code", "de": "Code scannen",
        "hi": "कोड स्कैन करें", "pt": "Escanear código", "ru": "Skanirovat' kod", "zh": "扫描代码", "ja": "コードをスキャン"
    },
    "qr_generate_tab": {
        "en": "Generate Code", "es": "Generar código", "fr": "Générer le code", "de": "Code generieren",
        "hi": "कोड जेनरेट करें", "pt": "Gerar código", "ru": "Sozdat' kod", "zh": "生成代码", "ja": "コードを生成"
    },
    "qr_scan_cancelled": {
        "en": "Scanning cancelled or failed.", "es": "Escaneo cancelado o fallido.", "fr": "Numérisation annulée ou échouée.", "de": "Scan abgebrochen oder fehlgeschlagen.",
        "hi": "स्कैनिंग रद्द या विफल रही।", "pt": "Varredura cancelada ou falhou.", "ru": "Skanirovanie otmeneno ili ne udalos'", "zh": "扫描已取消或失败。", "ja": "スキャンがキャンセルされたか、失敗しました।"
    },
    "qr_scan_result_header": {
        "en": "Scan Result:", "es": "Resultado del escaneo:", "fr": "Résultat du scan :", "de": "Scan-Ergebnis:",
        "hi": "स्कैन परिणाम:", "pt": "Resultado do escaneamento:", "ru": "Rezul'tat skanirovanija:", "zh": "扫描结果：", "ja": "スキャン結果："
    },
    "qr_scanned_text_clip": {
        "en": "Scanned Text", "es": "Texto escaneado", "fr": "Texte scanné", "de": "Gescannter Text",
        "hi": "स्कैन किया गया टेक्स्ट", "pt": "Texto escaneado", "ru": "Otskanirovannyj tekst", "zh": "扫描的文本", "ja": "スキャンされたテキスト"
    },
    "qr_copied_toast": {
        "en": "Copied to clipboard", "es": "Copiado al portapapeles", "fr": "Copié dans le presse-papiers", "de": "In die Zwischenablage kopiert",
        "hi": "क्लिपबोर्ड पर कॉपी किया गया", "pt": "Copiado para a área de transferência", "ru": "Skopirovano v bufer obmena", "zh": "已复制到剪贴板", "ja": "クリップボードにコピーしました"
    },
    "qr_copy_btn": {
        "en": "Copy Text", "es": "Copiar texto", "fr": "Copier le texte", "de": "Text kopieren",
        "hi": "टेक्स्ट कॉपी करें", "pt": "Copiar texto", "ru": "Kopirovat' tekst", "zh": "复制文本", "ja": "テキストをコピー"
    },
    "qr_open_link_btn": {
        "en": "Open Link", "es": "Abrir enlace", "fr": "Ouvrir le lien", "de": "Link öffnen",
        "hi": "लिंक खोलें", "pt": "Abrir link", "ru": "Otkryt' ssylku", "zh": "打开链接", "ja": "リンクを開く"
    },
    "qr_generate_placeholder": {
        "en": "Enter URL or text to generate", "es": "Introduzca la URL o el texto a generar", "fr": "Saisir l'URL ou le texte à générer", "de": "URL oder Text zum Generieren eingeben",
        "hi": "जेनरेट करने के लिए यूआरएल या टेक्स्ट दर्ज करें", "pt": "Insira a URL ou texto para gerar", "ru": "Vvedite URL ili tekst dlja sozdanija", "zh": "输入要生成的网址或文本", "ja": "生成するURLまたはテキストを入力してください"
    },
    "qr_generate_btn": {
        "en": "Generate QR", "es": "Generar QR", "fr": "Générer le QR", "de": "QR generieren",
        "hi": "क्यूआर जेनरेट करें", "pt": "Gerar QR", "ru": "Sozdat' QR", "zh": "生成二维码", "ja": "QRを生成"
    },
    "qr_save_gallery": {
        "en": "Save to Gallery", "es": "Guardar en la galería", "fr": "Enregistrer dans la galerie", "de": "In der Galerie speichern",
        "hi": "गैलरी में सहेजें", "pt": "Salvar na galeria", "ru": "Sohranit' v galereju", "zh": "保存到相册", "ja": "ギャラリーに保存"
    },
    "scanner_saved_downloads": {
        "en": "Saved scan to Downloads: %1$s", "es": "Escaneo guardado en Descargas: %1$s", "fr": "Scan enregistré dans Téléchargements : %1$s", "de": "Scan in Downloads gespeichert: %1$s",
        "hi": "डाउनलोड में स्कैन सहेजा गया: %1$s", "pt": "Escaneamento salvo em Downloads: %1$s", "ru": "Skan sohranen v Zagruzki: %1$s", "zh": "已保存扫描至下载目录：%1$s", "ja": "スキャンをダウンロードに保存しました: %1$s"
    },
    "scanner_failed_downloads": {
        "en": "Failed to copy file to downloads.", "es": "Error al copiar el archivo a descargas.", "fr": "Échec de la copie du fichier dans les téléchargements.", "de": "Fehler beim Kopieren der Datei in die Downloads.",
        "hi": "फ़ाइल को डाउनलोड में कॉपी करने में विफल।", "pt": "Falha ao copiar o arquivo para downloads.", "ru": "Ne udalos' skopirovat' fajl v zagruzki.", "zh": "复制文件到下载目录失败。", "ja": "ファイルをダウンロードにコピーできませんでした।"
    },
    "scanner_saved_locker": {
        "en": "Scan encrypted and saved to Vault.", "es": "Escaneo cifrado y guardado en la bóveda.", "fr": "Scan crypté et enregistré dans le coffre-fort.", "de": "Scan verschlüsselt und im Tresor gespeichert.",
        "hi": "स्कैन को एन्क्रिप्ट किया गया और तिजोरी में सहेजा गया।", "pt": "Escaneamento criptografado e salvo no cofre.", "ru": "Skan zashifrovan i sohranen v sejf.", "zh": "扫描已加密并保存到保险箱。", "ja": "スキャンは暗号化され、保管庫に保存されました।"
    },
    "scanner_failed_locker": {
        "en": "Locker encryption failed.", "es": "Cifrado de la bóveda fallido.", "fr": "Échec du cryptage du coffre-fort.", "de": "Safe-Verschlüsselung fehlgeschlagen.",
        "hi": "लॉकर एन्क्रिप्शन विफल रहा।", "pt": "Falha na criptografia do cofre.", "ru": "Oshibka shifrovanija sejfa.", "zh": "保险箱加密失败。", "ja": "保管庫の暗号化に失敗しました।"
    },
    "scanner_screen_title": {
        "en": "Document Scanner", "es": "Escáner de documentos", "fr": "Scanner de documents", "de": "Dokumentenscanner",
        "hi": "दस्तावेज़ स्कैनर", "pt": "Escâner de documentos", "ru": "Skaner dokumentov", "zh": "文档扫描仪", "ja": "ドキュメントスキャナー"
    },
    "scanner_empty_title": {
        "en": "Scan Physical Documents", "es": "Escanear documentos físicos", "fr": "Scanner des documents physiques", "de": "Physische Dokumente scannen",
        "hi": "भौतिक दस्तावेज़ स्कैन करें", "pt": "Escanear documentos físicos", "ru": "Skanirovanie bumazhnyh dokumentov", "zh": "扫描纸质文档", "ja": "紙のドキュメントをスキャン"
    },
    "scanner_empty_desc": {
        "en": "Auto-detects edges, corrects perspective, and optimizes contrast dynamically. Makes clean multipage PDFs.", "es": "Detecta automáticamente bordes, corrige la perspectiva y optimiza el contraste de forma dinámica. Crea PDF multipágina limpios.", "fr": "Détecte automatiquement les contours, corrige la perspective et optimise le contraste de manière dynamique. Crée des PDF multipages propres.", "de": "Erkennt automatisch Kanten, korrigiert die Perspektive und optimiert den Kontrast dynamisch. Erstellt saubere, mehrseitige PDFs.",
        "hi": "स्वचालित रूप से किनारों का पता लगाता है, परिप्रेक्ष्य को ठीक करता है, और कंट्रास्ट को गतिशील रूप से अनुकूलित करता है। साफ बहुपृष्ठ पीडीएफ बनाता है।", "pt": "Detecta automaticamente bordas, corrige a perspectiva e otimiza o contraste dinamicamente. Cria PDFs multipáginas limpos.", "ru": "Avtomaticheski opredeljaet kraja, ispravljaet perspektivu i dinamicheski optimiziruet kontrastnost'. Sozdaet chetkie mnogostranichnye PDF.", "zh": "自动检测边缘、校正透视并动态优化对比度。制作干净的多页 PDF。", "ja": "輪郭を自動検出し、遠近感を補正し、コントラストを動的に最適化します。きれいな複数ページのPDFを作成します।"
    },
    "scanner_init_error": {
        "en": "Scanning initiation error.", "es": "Error al iniciar el escaneo.", "fr": "Erreur d'initiation du scan.", "de": "Fehler beim Initiieren des Scans.",
        "hi": "स्कैनिंग शुरू करने में त्रुटि।", "pt": "Erro ao iniciar o escaneamento.", "ru": "Oshibka zapuska skanirovanija.", "zh": "启动扫描错误。", "ja": "スキャン開始エラー।"
    },
    "scanner_launch_btn": {
        "en": "Launch Scanner", "es": "Iniciar escáner", "fr": "Lancer le scanner", "de": "Scanner starten",
        "hi": "स्कैनर चालू करें", "pt": "Iniciar escâner", "ru": "Zapustit' skaner", "zh": "启动扫描仪", "ja": "スキャナーを起動"
    },
    "scanner_pages_scanned": {
        "en": "%1$d Page(s) Scanned", "es": "%1$d página(s) escaneada(s)", "fr": "%1$d page(s) scannée(s)", "de": "%1$d Seite(n) gescannt",
        "hi": "%1$d पृष्ठ स्कैन किए गए", "pt": "%1$d página(s) escaneada(s)", "ru": "Otskanirovano stranic: %1$d", "zh": "已扫描 %1$d 页", "ja": "%1$d ページをスキャンしました"
    },
    "scanner_discard_btn": {
        "en": "Discard Scan", "es": "Descartar escaneo", "fr": "Abandonner le scan", "de": "Scan verwerfen",
        "hi": "स्कैन रद्द करें", "pt": "Descartar escaneamento", "ru": "Sbrosit' skan", "zh": "放弃扫描", "ja": "スキャンを破棄"
    },
    "scanner_save_btn": {
        "en": "Save Document", "es": "Guardar documento", "fr": "Enregistrer le document", "de": "Dokument speichern",
        "hi": "दस्तावेज़ सहेजें", "pt": "Salvar documento", "ru": "Sohranit' dokument", "zh": "保存文档", "ja": "दキュメントを保存"
    },
    "scanner_save_dialog_title": {
        "en": "Save Scanned PDF", "es": "Guardar PDF escaneado", "fr": "Enregistrer le PDF scanné", "de": "Gescannte PDF speichern",
        "hi": "स्कैन किए गए पीडीएफ को सहेजें", "pt": "Salvar PDF escaneado", "ru": "Sohranit' otskanirovannyj PDF", "zh": "保存扫描的 PDF", "ja": "スキャンされたPDFを保存"
    },
    "scanner_save_dialog_desc": {
        "en": "Choose a destination path. Normal Downloads can be read by other apps. Private Locker is encrypted and locked.", "es": "Elija una ruta de destino. Las descargas normales pueden ser leídas por otras aplicaciones. El casillero privado está cifrado y bloqueado.", "fr": "Choisissez un chemin de destination. Les téléchargements normaux peuvent être lus par d'autres applications. Le casier privé est crypté et verrouillé.", "de": "Wählen Sie einen Zielpfad. Normale Downloads können von anderen Apps gelesen werden. Der private Safe ist verschlüsselt und gesperrt.",
        "hi": "एक गंतव्य पथ चुनें। सामान्य डाउनलोड अन्य ऐप्स द्वारा पढ़े जा सकते हैं। निजी लॉकर एन्क्रिप्टेड और लॉक है।", "pt": "Escolha um caminho de destino. Downloads normais podem ser lidos por outros aplicativos. O cofre privado é criptografado e bloqueado.", "ru": "Выберите путь назначения. Обычные загрузки доступны другим приложениям. Защищенный сейф зашифрован и заблокирован.", "zh": "选择目标路径。常规下载可以被其他应用读取。私密保险箱是加密且锁定的。", "ja": "保存先を選択してください。通常のダウンロードは他のアプリから読み取ることができます。プライベートロッカーは暗号化され、ロックされます।"
    },
    "scanner_save_private_btn": {
        "en": "Save Privately", "es": "Guardar de forma privada", "fr": "Enregistrer en privé", "de": "Privat speichern",
        "hi": "गोपनीय तरीके से सहेजें", "pt": "Salvar de forma privada", "ru": "Sohranit' konfidencial'no", "zh": "私密保存", "ja": "プライベートに保存"
    },
    "scanner_save_downloads_btn": {
        "en": "Save to Downloads", "es": "Guardar en Descargas", "fr": "Enregistrer dans les téléchargements", "de": "In Downloads speichern",
        "hi": "डाउनलोड में सहेजें", "pt": "Salvar em Downloads", "ru": "Sohranit' v Zagruzki", "zh": "保存到下载目录", "ja": "ダウンロードに保存"
    },
    "video_player_playback_error": {
        "en": "Playback error", "es": "Error de reproducción", "fr": "Erreur de lecture", "de": "Wiedergabefehler",
        "hi": "प्लेबैक त्रुटि", "pt": "Erro de reprodução", "ru": "Oshibka vosproizvedenija", "zh": "播放错误", "ja": "再生エラー"
    },
    "video_player_m3u8_fallback": {
        "en": "Could not fetch custom qualities; using source stream.", "es": "No se pudieron obtener calidades personalizadas; usando flujo de origen.", "fr": "Impossible de récupérer les qualités personnalisées ; utilisation du flux source.", "de": "Benutzerdefinierte Qualitäten konnten nicht abgerufen werden; Quellstream wird verwendet.",
        "hi": "कस्टम गुणवत्ता प्राप्त करने में असमर्थ; स्रोत स्ट्रीम का उपयोग कर रहे हैं।", "pt": "Não foi possível buscar qualidades personalizadas; usando fluxo de origem.", "ru": "Ne udalos' poluchit' pol'zovatel'skie kachestva; ispol'zuetsja ishodnyj potok.", "zh": "无法获取自定义清晰度；正在使用源流。", "ja": "カスタム品質を取得できませんでした。ソースストリームを使用します。"
    },
    "video_player_select_quality": {
        "en": "Select Download Quality", "es": "Seleccionar calidad de descarga", "fr": "Sélectionner la qualité de téléchargement", "de": "Download-Qualität auswählen",
        "hi": "डाउनलोड गुणवत्ता चुनें", "pt": "Selecionar qualidade de download", "ru": "Выберите качество загрузки", "zh": "选择下载清晰度", "ja": "ダウンロード品質の選択"
    },
    "video_player_download_desc": {
        "en": "Download video streams with dynamic formats securely in the background.", "es": "Descargue transmisiones de video con formatos dinámicos de forma segura en segundo plano.", "fr": "Téléchargez des flux vidéo avec des formats dynamiques en toute sécurité en arrière-plan.", "de": "Videostreams mit dynamischen Formaten sicher im Hintergrund herunterladen.",
        "hi": "पृष्ठभूमि में सुरक्षित रूप से गतिशील प्रारूपों के साथ वीडियो स्ट्रीम डाउनलोड करें।", "pt": "Baixe transmissões de vídeo com formatos dinâmicos com segurança em segundo plano.", "ru": "Bezopasno skachivajte videopotoki v dinamicheskih formatah v fonovom rezhime.", "zh": "在后台安全下载具有动态格式的视频流。", "ja": "バックグラウンドで動的な形式のビデオストリームを安全にダウンロードします。"
    },
    "video_player_save_to_locker": {
        "en": "Save to Private Locker", "es": "Guardar en casillero privado", "fr": "Enregistrer dans le casier privé", "de": "Im privaten Safe speichern",
        "hi": "निजी लॉकर में सहेजें", "pt": "Salvar no cofre privado", "ru": "Sohranit' v privatnyj sejf", "zh": "保存到私密保险箱", "ja": "プライベートロッカーに保存"
    },
    "video_player_encrypt_desc": {
        "en": "Encrypt & secure with passcode", "es": "Cifrar y proteger con contraseña", "fr": "Crypter et sécuriser avec un mot de passe", "de": "Mit Passcode verschlüsseln und sichern",
        "hi": "पासकोड के साथ एन्क्रिप्ट और सुरक्षित करें", "pt": "Criptografar e proteger com senha", "ru": "Zashifrovat' i zashhitit' parolem", "zh": "使用密码加密和保护", "ja": "パスコードで暗号化して保護"
    },
    "video_player_queued_locker": {
        "en": "Queued secure download to Locker!", "es": "¡Descarga segura en cola para el casillero!", "fr": "Téléchargement sécurisé mis en file d'attente vers le casier !", "de": "Sicherer Download in den Safe eingereiht!",
        "hi": "लॉकर में सुरक्षित डाउनलोड कतारबद्ध!", "pt": "Download seguro na fila para o cofre!", "ru": "Bezopasnaja zagruzka dobavlena v ochered' sejfa!", "zh": "安全下载已排队至保险箱！", "ja": "ロッカーへの安全なダウンロードがキューに追加されました！"
    },
    "video_player_queued_download": {
        "en": "Queued %1$s download!", "es": "¡Descarga %1$s en cola!", "fr": "Téléchargement %1$s mis en file d'attente !", "de": "%1$s Download eingereiht!",
        "hi": "कतारबद्ध %1$s डाउनलोड!", "pt": "Download %1$s na fila!", "ru": "Zagruzka %1$s dobavlena v ochered'!", "zh": "%1$s 下载已排队！", "ja": "%1$s のダウンロードがキューに追加されました！"
    },
    "video_player_downloading": {
        "en": "Downloading", "es": "Descargando", "fr": "Téléchargement en cours", "de": "Wird heruntergeladen",
        "hi": "डाउनलोड हो रहा है", "pt": "Baixando", "ru": "Skachivanie", "zh": "正在下载", "ja": "ダウンロード中"
    },
    "video_player_download_cancelled": {
        "en": "Download cancelled", "es": "Descarga cancelada", "fr": "Téléchargement annulé", "de": "Download abgebrochen",
        "hi": "डाउनलोड रद्द किया गया", "pt": "Download cancelado", "ru": "Zagruzka otmenena", "zh": "下载已取消", "ja": "ダウンロードがキャンセルされました"
    },
    "video_player_download_complete": {
        "en": "Download Complete!", "es": "¡Descarga completada!", "fr": "Téléchargement terminé !", "de": "Download abgeschlossen!",
        "hi": "डाउनलोड पूर्ण!", "pt": "Download concluído!", "ru": "Zagruzka zavershena!", "zh": "下载完成！", "ja": "ダウンロード完了！"
    }
}

languages = ["en", "es", "fr", "de", "hi", "pt", "ru", "zh", "ja"]

def main():
    print("Generating strings.xml resource files...")
    
    for lang in languages:
        # Determine directory path
        if lang == "en":
            dir_path = "app/src/main/res/values"
        else:
            dir_path = f"app/src/main/res/values-{lang}"
            
        # Ensure directories exist
        os.makedirs(dir_path, exist_ok=True)
        
        filepath = os.path.join(dir_path, "strings.xml")
        
        # Build XML content
        xml_lines = [
            '<?xml version="1.0" encoding="utf-8"?>',
            '<resources>'
        ]
        
        for key, translations in strings_dict.items():
            val = translations.get(lang, translations["en"])
            # Escape XML special characters
            escaped_val = val.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;').replace('"', '\\"').replace("'", "\\'")
            xml_lines.append(f'    <string name="{key}">{escaped_val}</string>')
            
        xml_lines.append('</resources>')
        
        # Write to file
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write("\n".join(xml_lines) + "\n")
            
        print(f"Generated successfully: {filepath}")

if __name__ == '__main__':
    main()
