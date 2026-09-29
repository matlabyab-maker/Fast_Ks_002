package com.fkeys.eleven;

import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.view.*;
import android.view.inputmethod.InputConnection;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.util.*;

public class FastKeyboardView extends View {
    private final FastKeyboardInputMethodService service;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Pressed-key visual feedback: follows the supplied reference implementation.
    private final Paint pressedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private float gap, keyH;
    // Caps states: 0=normal, 1=one-tap shift, 2=double-tap lock.
    private int capsMode = 0;
    private boolean capsHold = false;
    private long lastCapsTap = 0L;
    private boolean capsHoldTriggered = false;
    private boolean equalUnderscoreNext = true;
    private boolean resizeRollerOpen = false;
    private float keyboardScale = 1.0f;
    private static final float BASE_KEYBOARD_DP = 380f;
    private static final float RESIZE_ROLLER_DP = 52f;
    private boolean alifLongPressTriggered = false;
    private final Runnable alifLongPressAction = () -> {
        alifLongPressTriggered = true;
        showAlifArabicPicker();
        invalidate();
    };
    private final Runnable capsHoldAction = () -> {
        capsHoldTriggered = true;
        capsHold = true;
        invalidate();
    };
    private Runnable repeat;
    private float repeatX, repeatY;
    // Visual key-press light; this changes only the pressed-key appearance.
    private boolean pressGlow = false;
    private boolean pressHeld = false;
    private float pressL, pressT, pressR, pressB;
    private final Runnable clearPressGlow = () -> {
        if (!pressHeld) {
            pressGlow = false;
            invalidate();
        }
    };
    private final String[] suggestions = new String[6];
    private final Runnable delayedSuggestionRefresh = () -> refreshSuggestions();
    private void requestSuggestionsRefresh(){
        handler.removeCallbacks(delayedSuggestionRefresh);
        handler.postDelayed(delayedSuggestionRefresh, 70);
    }
    private float suggestionH;
    private static final String[][] WORDS = {
        {"سلام","سلامت","سلامتی"},{"من","منم","منطقه"},{"این","اینجا","اینجانب"},
        {"برای","برنامه","بررسی"},{"کیبورد","کیبوردی","کیبوردها"},{"است","استفاده","استان"},
        {"یک","یکی","یکم"},{"دارم","دارد","دارند"},{"می","میرم","میز"},{"خوب","خوبه","خوبی"},
        {"تایپ","تایپی","تایپ کردن"},{"کلمه","کلمات","کلمه‌های"}
    };
    private static final String[] TOPIC_WORDS = (
        "منظومه شمسی خورشید سیاره عطارد زهره زمین مریخ مشتری زحل اورانوس نپتون پلوتون ماه قمر ستاره " +
        "کهکشان راه شیری سحابی شهاب شهاب سنگ سیارک دنباله دار مدار جاذبه گرانش تلسکوپ رصدخانه نجوم " +
        "ستاره شناسی کیهان اختر اخترشناسی کسوف خسوف صورت فلکی نور سال نوری ابر اورت " +
        "بیمه بیمه کار بیمه درمان بیمه درمانی بیمه تکمیلی بیمه تامین اجتماعی بیمه سلامت بیمه عمر " +
        "بیمه بیکاری بیمه حوادث حق بیمه فرانشیز خسارت غرامت بیمه نامه بیمه شده بیمه گر کارفرما کارگر " +
        "بازنشستگی مستمری دفترچه درمان پرونده درمانی پوشش بیمه ای هزینه درمان قرارداد بیمه " +
        "نگهداری کودک مهدکودک مهد کودک پرستار کودک مراقبت کودک پیش دبستانی کودکستان مدرسه دبستان " +
        "راهنمایی دبیرستان هنرستان دانش آموز معلم مدیر مدرسه کلاس درس امتحان آزمون نمره کارنامه " +
        "دانشگاه دانشجو استاد دانشکده رشته تحصیلی کارشناسی کارشناسی ارشد دکترا دکتری پژوهش پژوهشگر " +
        "پایان نامه رساله ترم واحد درسی آموزشگاه مرکز آموزشی کلاس آنلاین آموزش عالی"
    ).split(" ");

    private static final String[] EXTRA_WORDS = (
        "درمان پزشکی سلامت دارو دارویی داروها پزشک پزشکان بیمار بیماران بیماری بیماریها درمانگر پرستار" +
        "پرستاری بیمارستان درمانگاه کلینیک مطب نسخه داروخانه داروساز داروسازی قرص کپسول شربت آمپول تزریق" +
        "واکسن واکسیناسیون آنتیبیوتیک مسکن تب درد سردرد میگرن سرفه سرماخوردگی آنفلوآنزا عفونت التهاب حساسیت" +
        "آلرژی فشارخون قند خون دیابت قلب مغز ریه کلیه کبد معده روده پوست چشم گوش بینی دندان دهان استخوان" +
        "مفصل عضله آزمایش آزمایشگاه تشخیص علائم پیشگیری اورژانس جراحی عمل مراقبت روان تغذیه ویتامین مکمل" +
        "پروتئین رژیم غذایی کالری خواب ورزش توانبخشی فیزیوتراپی روانشناسی روانپزشکی استرس اضطراب افسردگی" +
        "حافظه سرطان تومور ویروس باکتری قارچ کرونا کووید اکسیژن نبض فشار دما تبخال زخم سوختگی شکستگی سرگیجه" +
        "تهوع استفراغ اسهال یبوست خونریزی دردناک سالم سالمند کودک نوزاد بارداری باردار زایمان مادر جنین \"" +
        "\"فناوری فناوری اطلاعات تکنولوژی کامپیوتر رایانه لپتاپ لپتاپها دسکتاپ سرور شبکه اینترنت وب سایت" +
        "وبسایت مرورگر موتور جستجو گوگل بینگ فایرفاکس کروم اندروید ویندوز لینوکس مک او اس آیفون آیپد اپل" +
        "سامسونگ شیائومی هواوی نوکیا پیکسل وانپلاس سونی ال جی ایسوس لنوو دل اچ پی ایسر گیگابایت ترابایت" +
        "مگابایت کیلوبایت پردازنده CPU GPU RAM ROM SSD HDD USB HDMI بلوتوث وایفای WiFi 5G 4G LTE مودم روتر" +
        "سوئیچ کابل شارژر باتری نمایشگر صفحه نمایش دوربین سنسور نرم افزار سخت سیستم عامل اپلیکیشن برنامه" +
        "نویسی کدنویسی توسعه دهنده مهندسی داده پایگاه دیتابیس SQL API SDK IDE کامپایلر مفسر دیباگ دیباگر خطا" +
        "باگ تست واحد یونیت ورژن انتشار ریلیز مخزن repository گیت Git GitHub GitLab commit push pull branch" +
        "merge clone fork workflow action Gradle Maven Java Kotlin Python C C++ JavaScript TypeScript HTML" +
        "CSS XML JSON YAML HTTP HTTPS URL DNS IP TCP UDP SSH SSL TLS Linux Android Studio Visual Code VSCode" +
        "IntelliJ Eclipse Flutter React Native NodeJS npm package library framework class object method" +
        "function variable constant array list map string integer boolean null true false public private" +
        "protected static final override interface import export return if else switch case for while try" +
        "catch exception activity service view layout widget manifest resource drawable build apk aab debug" +
        "release \"سیاسی سیاست دولت حکومت مجلس وزارت وزیر رئیس جمهور ریاست جمهوری پارلمان انتخابات رای قانون" +
        "قانونگذاری نماینده نمایندگان حزب سیاستمدار سیاستمداران کشور روابط بین الملل دیپلماسی دیپلمات سفارت" +
        "سفیر سازمان ملل متحد شورای امنیت تحریم اقتصاد بودجه مالیات دفاع ارتش نیروهای مسلح پلیس اساسی حقوق" +
        "شهروندی جامعه مدنی آزادی عدالت دادگاه قاضی دادستان پرونده محلی فدرال پادشاهی نخست \"مذهبی دین ادیان" +
        "اسلام مسلمان قرآن کریم حدیث احادیث دعا نماز روزه حج عمره زکات صدقه مسجد محراب منبر امام پیامبر" +
        "پیامبران رسول خدا حضرت محمد علی فاطمه حسن حسین مهدی شیعه سنی اهل سنت روحانی عالم فقیه مرجع تقلید" +
        "تفسیر سوره آیه کعبه مکه مدینه کربلا نجف عاشورا محرم رمضان عید فطر قربان اذان وضو تیمم قبله \"جنگ" +
        "جنگی نبرد سرباز فرمانده ژنرال دریادار نیروی هوایی دریایی تانک توپ توپخانه موشک هواپیما جنگنده بمب" +
        "افکن ناو زیردریایی کشتی سلاح مهمات جبهه خط مقدم عملیات حمله پیروزی شکست آتش بس صلح پیمان معاهده" +
        "اشغال مقاومت نیرو دشمن تاریخ نظامی استراتژی تاکتیک جاسوسی جاسوس پناهگاه سنگر لشکر گردان هنگ تیپ" +
        "لشکرکشی جهانی اول جنگ دوم اولی دومی WWI WW1 WWII WW2 WorldWar World War One Two تاریخی باستان" +
        "باستانی قرون وسطی قرن انقلاب امپراتوری پادشاه شاه ملکه سلطنت روم یونان ایران پارس عثمانی بریتانیا" +
        "فرانسه آلمان روسیه شوروی آمریکا ژاپن ایتالیا اتریش مجارستان لهستان چکسلواکی نازی فاشیسم متفقین محور" +
        "هیتلر چرچیل استالین روزولت ترومن موسولینی پرل هاربر نورماندی نرماندی استالینگراد برلین مسکو لندن" +
        "پاریس ورشو هیروشیما ناکازاکی \"برنامه برنامه‌نویسی الگوریتم ساختار متغیر تابع کلاس شیء رشته آرایه" +
        "لیست مجموعه دیکشنری حلقه شرط شرطی ورودی خروجی خطایابی اشکال زدایی تستر مستندات مستندسازی کامنت" +
        "کامپایل اجرا سورس کد منبع پروژه طراحی رابط کاربری UI تجربه UX فرانت اند بک فول استک اپ موبایل" +
        "کلاینت درخواست پاسخ ریکوئست ریسپانس استثنا ارور error warning هشدار log لاگ terminal ترمینال shell" +
        "bash powershell script اسکریپت thread process پردازش memory cache کش queue صف stack پشته recursion" +
        "بازگشتی async همزمانی concurrency parallel موازی encryption رمزنگاری hash هش token توکن" +
        "authentication احراز هویت authorization مجوز permission دسترسی رمز عبور password username نام" +
        "\"ماشین خودرو خودروها اتومبیل سواری وانت کامیون اتوبوس مینی بوس تاکسی موتورسیکلت دوچرخه قطار مترو" +
        "هلیکوپتر نیسان تویوتا لکسوس هوندا مزدا سوبارو میتسوبیشی سوزوکی کیا هیوندای جنسیس فورد شورولت" +
        "کادیلاک جیپ دوج تسلا مرسدس بنز بی ام و BMW آئودی فولکس واگن پورشه فراری لامبورگینی مازراتی ولوو رنو" +
        "پژو سیتروئن فیات بوگاتی لارن مک‌لارن رولزرویس بنتلی استون مارتین لندرور رنجروور سانتافه توسان" +
        "النترا سوناتا سمند دنا پراید تیبا شاهین \"گوشی تلفن هوشمند تبلت iPhone iOS Galaxy گلکسی S24 S25 A55" +
        "A35 Note Pixel Xperia Redmi Poco Mi Xiaomi Huawei Honor OnePlus Nokia Motorola Asus ROG Zenfone" +
        "\"لباس پوشاک پیراهن شلوار کت کاپشن پالتو مانتو روسری شال کلاه کفش کتانی بوت جوراب لباس زیر تی شرت" +
        "تی‌شرت ژاکت سویشرت هودی دامن مجلسی ورزشی کیف کوله پشتی چمدان کمربند دستکش عینک ساعت پارچه پنبه پشم" +
        "ابریشم جین چرم اندازه سایز کوچک متوسط بزرگ XL XXL \"انسان آدم انسانها شخص افراد فرد مردم مرد زن بچه" +
        "نوجوان جوان بزرگسال اسم خانوادگی خانواده پدر برادر خواهر پسر دختر دوست همکار مدیر کارمند معلم استاد" +
        "دانشجو دانش آموز مهندس نویسنده خبرنگار هنرمند بازیگر کارگردان ورزشکار راننده خلبان پژوهشگر دانشمند" +
        "\"حیوان حیوانات سگ گربه اسب گاو گوسفند بز شتر الاغ فیل شیر ببر پلنگ یوز خرس گرگ روباه خرگوش موش" +
        "میمون گوریل شامپانزه زرافه فلامینگو طوطی کبوتر عقاب جغد کلاغ مرغ خروس اردک غاز ماهی کوسه نهنگ دلفین" +
        "لاکپشت مار سوسمار تمساح قورباغه پروانه زنبور مورچه عنکبوت عقرب \"شهر شهرها کشورها جهان دنیا تهران" +
        "مشهد اصفهان شیراز تبریز قم کرج اهواز رشت ارومیه یزد کرمان همدان بندرعباس ساری زاهدان اردبیل" +
        "استانبول آنکارا بغداد دمشق بیروت ریاض دبی ابوظبی دوحه کویت دهلی بمبئی پکن شانگهای توکیو سئول بانکوک" +
        "سنگاپور جاکارتا سیدنی ملبورن سن پترزبورگ رم مادرید بارسلونا لیسبون آمستردام بروکسل وین زوریخ ژنو" +
        "پراگ آتن قاهره اسکندریه کیپ تاون نایروبی نیویورک واشنگتن لس آنجلس سان فرانسیسکو شیکاگو تورنتو" +
        "ونکوور مکزیکوسیتی ریو سائوپائولو بوئنوس آیرس \"کشورها عراق سوریه لبنان اردن عربستان امارات قطر عمان" +
        "بحرین ترکیه آذربایجان ارمنستان گرجستان افغانستان پاکستان هند چین کره جنوبی شمالی اوکراین بلاروس" +
        "قزاقستان ازبکستان ترکمنستان تاجیکستان کانادا مکزیک برزیل آرژانتین شیلی پرو کلمبیا انگلیس اسپانیا" +
        "پرتغال هلند بلژیک سوئیس چک سوئد نروژ دانمارک فنلاند ایسلند استرالیا نیوزیلند مصر آفریقای مراکش" +
        "الجزایر تونس لیبی نیجریه کنیا \"جاهای دیدنی مکان گردشگری موزه کاخ قلعه برج میدان پل کلیسا معبد" +
        "زیارتگاه دانشگاه کتابخانه پارک باغ شهربازی ورزشگاه فرودگاه ایستگاه راه آهن بندر ساحل جزیره دریاچه" +
        "رودخانه آبشار کوه قله غار بیابان جنگل نقش تخت جمشید پاسارگاد ارگ بم میلاد گلستان حرم رضا معصومه ملی" +
        "لوور ایفل کلیسای نوتردام بیگ بن آکروپولیس کولوسئوم واتیکان تاج محل دیوار شهر ممنوعه فوجی هالیوود" +
        "مجسمه سفید دیزنی لند \"کتاب نوشتن نویسندگان داستان رمان شعر شاعر ادبیات مقاله مجله روزنامه فصل بخش" +
        "عنوان متن پاراگراف جمله کلمه واژه حرف حروف نقطه ویرگول علامت سوال تعجب نقل قول پرانتز فاصله تایپ" +
        "کردن ویرایشگر ویرایش ذخیره چاپ پرینت کپی پیست برش چسباندن انتخاب همه جایگزینی پیدا بازگشت برگشت جلو" +
        "عقب کلید قلم مداد دفتر یادداشت \"گفتگو پیشنهاد اصلاح ها پیشنهادی درون اضافه کن سلام سلامتی سیاسی" +
        "مذهبی ماشین گوشی انسان حیوان کتاب استثنای غیر بغیر از به که گروه دست دسته رسته راستای بگیر بدهی" +
        "بدهیم بدهد اینها ایجاد کردیم گذاشتم گذاشتیم ساختم ساخت ساختیم پوشه های رنگها امتحان بیاریم خطها سطر" +
        "سطرها شماره تیم دگرگون سازی زیرین پایین ترین بالاترین گم شد نشود شاید ممکنه امکان داریم داشت زشت" +
        "زیبا تند سریع ارزش گفتگوی گفتگو یمان هر هرچه هست نیست پیشرفته حرفه ای شبیه ردیف ردیفهای چند چندمین" +
        "هرچند با اینکه تا چرا ؟ ،\" \"لیست چکار کنم بدرد استفاده ضروری ضرورت قابل طول عرض یکسان ناهمسان" +
        "نابرابر نابرابری دقیقا دقیق دقت قرار بده پنهان ظاهر مشترک متفاوت مختلف هم دیگر دیگری همدیگر یکدیگر" +
        "بلد شما تو اینکار باهم بدون بگذار بگذاریم کمک کمکت مخفی نگهدار نگه ثابت باش باشد باشیم نباید نباش" +
        "نباشیم نباشد عکس عکسها عکسهای را الا مگر Hidden Hide Yes Inter Enter Rest Go اینترنتی جا جایگذاری" +
        "جلوتر تر میان بود بودند ببین بررسی پیش نمودار هرم لایت نور تکسچر تکسچرهای پولیگان شب تاریک روز روشن" +
        "تیره ماندن بماند بساز در اینطرف سمت سمتهای صاف\" فلوات Float Fast Fast_ -maker _maker origin تیک" +
        "لینک تکست رند رندر رول Wheel شان یش گود کلفت سوم چهارم اولین بار درهمی درهم برهم تداخل منظم مناسب" +
        "بدترین خوب بد راست درست قرمز سبز زرد سورمه قهوه تنها فقط عمود عمودی افق افقی مچ پیچ مارپیچ درخت" +
        "کاراکتر کدگذاری کدخوانی بیلد Build Debug Log Source Repository ریپازیتوری گیتهاب گیتلب Commit پوش" +
        "Push Pull PullRequest Branch شاخه Merge ادغام Fork کلون Clone Release Version بسته Package Library" +
        "فریمورک Framework ادیتور Editor Terminal کنسول Console دستور Command Script ماژول Module Class متد" +
        "Method Function Variable Constant String عدد Integer Double Boolean Array List Set نقشه Map شی" +
        "Object وراثت Inheritance پلی مورفیسم Polymorphism Interface انتزاع Abstraction کپسوله Encapsulation" +
        "ارث بری Algorithm Data Database NoSQL Regex عبارت کنترل Test UnitTest یکپارچه IntegrationTest" +
        "اتوماسیون Automation وابستگی Dependency تنظیمات Configuration پیکربندی BuildConfig بازی گیم Game" +
        "بازیکن Player Enemy باس Boss NPC شخصیت مرحله Level محیط Environment انیمیشن Animation حرکت رفتن" +
        "دویدن پرش شلیک تیراندازی اسلحه زره جان امتیاز Score سکه Coin قدرت Power مأموریت Mission هدف Quest" +
        "پاداش Reward شروع پایان بارگذاری Save Load منو Menu دکمه Button کنترلر Controller جوی استیک" +
        "Joystick Gamepad فیزیک Physics برخورد Collision GameEngine Unity Unreal Godot Sprite Texture Shader" +
        "Material مدل سه بعدی دو 2D 3D مسافرکشی ون مینی‌بوس پهپاد بمب‌افکن ترابری شناسایی رهگیر زرهی نفربر" +
        "قایق آفرود سدان هاچبک کوپه کانورتیبل استیشن شاسی بلند SUV کراس‌اور ون‌کمپر کرایسلر موستانگ کامارو" +
        "کوروت فولکس‌واگن بی‌ام‌و اوپل دوو زانتیا پیکان جگوار غیرجنگی قدیمی جدید کلاسیک هواپیمای مسافربری" +
        "باری کابین باند پرواز برخاست فرود اوج‌گیری اضطراری ناوبری رادار توربین جت ملخ بال دم ارابه چرخ سوخت" +
        "مسیر پروازی ارتفاع سرعت راه‌آهن لوکوموتیو لکوموتیو مسافری سکو ریل ترمز لوکوموتیوران بلیت مسافر تونل" +
        "گیربکس جعبه دنده کلاچ فرمان لاستیک تایر دینام استارت رادیاتور فن روغن فیلتر شمع انژکتور کاربراتور" +
        "اگزوز فنر تعلیق جلوبندی بدنه چراغ راهنما آینه بغل داشبورد کیلومترشمار آمپر بنزین گاز پدال تعمیر" +
        "تعمیرکار مکانیک سرویس پنچر پنچرگیری یدک‌کش کیک شیرینی غذا پخت‌وپز آشپزی نان خمیر آرد شکر نمک" +
        "تخم‌مرغ پنیر خامه شکلات کاکائو وانیل دارچین زعفران عسل مربا ژله بستنی شکلاتی وانیلی اسفنجی خامه‌ای" +
        "میوه‌ای هویج چیزکیک براونی کوکی کلوچه پای تارت پیتزا پاستا ماکارونی برنج خورش سوپ آش کباب گوشت سبزی" +
        "سالاد ساندویچ همبرگر سیب‌زمینی ابزار فر اجاق مایکروویو همزن مخلوط‌کن غذاساز قابلمه تابه ماهیتابه" +
        "دیگ زودپز بخارپز چاقو رنده پوست‌کن تخته پیمانه ترازو قالب وردنه لیسک قاشق چنگال ملاقه انبر برشکاری" +
        "جوشکاری جوشکار دستگاه جوش اینورتر الکترود سیم ماسک ایمنی سنگ فرز اره برقی دستی دریل مته پیچ‌گوشتی" +
        "آچار انبردست گازبر کاتر تیغ کارگاه نجاری نجار چوب الوار MDF نئوپان میخ چسب سمباده گیره کارگاهی متر" +
        "تراز فوتبال فوتسال بسکتبال والیبال تنیس پینگ‌پنگ شنا بوکس رزمی دوچرخه‌سواری کوهنوردی راکت تور" +
        "دروازه داور مربی مسابقه لیگ قهرمانی تمرین بدن‌سازی باشگاه استخر تفریح سرگرمی سینما تئاتر سفر اردو" +
        "کمپ مغازه فروشگاه سوپرمارکت نانوایی قصابی رستوران کافه کتابفروشی تعمیرگاه بازار پاساژ فروشنده مشتری" +
        "صندوق انبار اداره شرکت کارخانه بانک بیمه مدرسه آموزشگاه کلانتری آتش‌نشانی اداری منشی حسابدار کارگر" +
        "پیک پستچی آرایشگر خیاط نانوا آشپز کشاورز باغبان بنا نقاش برقکار لوله‌کش نظافتچی نگهبان وکیل مترجم" +
        "عکاس طراح سرقت دزدی سارق دزد کلاهبرداری کلاه‌برداری کلاهبردار تقلب فریب جعل جعلی فیشینگ فیشینگینگ" +
        "هک هکر نفوذ حساب پیام فروش مالی پول‌شویی اختلاس رشوه حقوقی قانون‌گذاری متهم شاکی شاهد حکم رأی" +
        "دادخواست شکایت اعتراض تجدیدنظر بازداشت زندان وثیقه جرم مجازات مدرک سند قرارداد مالکیت طلاق ازدواج" +
        "کیفری دادسرا مأمور افسر بازپرس تحقیقات پلیسی گزارش صحنه اثر انگشت مداربسته گشت بازرسی دستگیری تعقیب" +
        "زندگی روزمره روزانه کار خانه خرید پول قیمت هزینه زمان امروز فردا دیروز صبح ظهر عصر هفته ماه سال" +
        "تعطیلات ملاقات تماس ایمیل لپ‌تاپ نرم‌افزار سخت‌افزار فایل تصویر ویدیو صدا موسیقی فیلم خبر آموزش" +
        "الکترونیک الکتریکی برق مدار برد الکترونیکی قطعه خازن دیود ترانزیستور رله فیوز سوکت پریز آداپتور" +
        "ولتاژ جریان وات توان فرکانس میکروکنترلر صفحه‌نمایش LED LCD OLED بلندگو میکروفون ماشین‌آلات تجهیزات" +
        "مانیتور کیبورد موس چاپگر اسکنر فلش مموری هارد کارت وای‌فای" +
        "دارد داد دا باید انگار نه بله هرگز انکار دور دورتر نزدیک گریز کنار درونش معرض دید دیدن پدید پدیدار نکته بلکه اما ولی چیدمان بچین نگذار ننویس نکش نبر توجه پهن باریک ریز درشت گرد مکعب دایره مربع نیمه نف ضرب ضربدر تقسیم تفاهم المان کوبل رچ نمره معادل گپ حرفها حرفهای تک یک جفت هرکدام هیچکدام تقریبا نمود نماد نشان نشانگر چهارتا دوتا پنج شش شنید شنیدن حس احساس مرطوب خشک صفر خالی تهی جور ناجور نامنظم نترس تلویزیون رادیو TV Radio Random Textures Video Tool Keyboard Fit [ ] سبک سنگین حذف پاک"
    ).split(" ");
    private final int BG=Color.rgb(239,238,232), DEFAULT_KEY=Color.rgb(250,249,244),
            BLUE=Color.rgb(20,112,235), NAVY=Color.rgb(18,38,78), NUMBER_BROWN=Color.rgb(116,58,24), SYMBOL_RED=Color.rgb(210,35,35), BLACK=Color.rgb(25,29,34),
            GREEN=Color.rgb(45,205,55), ENTER_BG=Color.rgb(225,238,255), BACKSPACE_BG=Color.rgb(255,232,232), NUMBER_BG=Color.rgb(232,231,224), SPACE_BG=Color.rgb(242,224,145);
    private boolean englishMode = false;
    private boolean hideTopRow = false;
    private boolean hideSuggestionRow = false;
    private static final float[] SOURCE_BANDS = {0f,122f,206f,342f,470f,600f,722f,856f};
    private int KEY;
    // Exact rectangles used both for drawing and touch hit-testing of the middle English row.
    private final ArrayList<RectF> middleEnglishKeyRects = new ArrayList<>();

    public FastKeyboardView(FastKeyboardInputMethodService s){
        super(s);
        service=s;
        KEY = service.getSharedPreferences("fast_keyboard_settings", android.content.Context.MODE_PRIVATE)
                .getInt("keyboard_key_color", DEFAULT_KEY);
        setBackgroundColor(BG);
        pressedPaint.setColor(Color.rgb(255, 220, 40));
        pressedPaint.setStyle(Paint.Style.FILL);
        glowPaint.setColor(Color.YELLOW);
        glowPaint.setStyle(Paint.Style.FILL);
        glowPaint.setShadowLayer(dp(12), 0f, 0f, Color.YELLOW);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        int savedAlpha = service.getSharedPreferences("fast_keyboard_settings", 0).getInt("keyboard_alpha", 100);
        setAlpha(Math.max(1, Math.min(100, savedAlpha)) / 100f);
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float h=getHeight();
        gap=dp(4);
        keyH=Math.max(1f,h/6f);
        suggestionH=0f;
        drawKeyboard(c);
    }

    private void txt(Canvas c,String s,float x,float y,float size,int color){
        p.setTypeface(Typeface.create("sans",Typeface.NORMAL));
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(s,x,y-(p.ascent()+p.descent())/2,p);
    }

    private void txtBold(Canvas c,String s,float x,float y,float size,int color){
        p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeWidth(Math.max(1.2f, size*0.075f));
        c.drawText(s,x,y-(p.ascent()+p.descent())/2,p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(1f);
    }

    private void txtAlphabet(Canvas c,String s,float x,float y,float size,int color){
        p.setTypeface(Typeface.create("sans",Typeface.BOLD));
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        p.setStyle(Paint.Style.FILL_AND_STROKE);
        p.setStrokeWidth(Math.max(0.45f, size*0.018f));
        c.drawText(s,x,y-(p.ascent()+p.descent())/2,p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(1f);
    }

    private void key(Canvas c,float l,float t,float r,float b,String label,int color,boolean square){
        p.setColor(KEY);
        p.setStyle(Paint.Style.FILL);
        float rad=square?3:7;
        c.drawRoundRect(l,t,r,b,rad,rad,p);
        p.setColor(Color.rgb(205,204,199));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        c.drawRoundRect(l,t,r,b,rad,rad,p);
        p.setStyle(Paint.Style.FILL);
        drawPressEffectIfNeeded(c,l,t,r,b,square?3:7);
        if(label!=null&&!label.isEmpty()){
            String[] parts=label.split("\\n",-1);
            if(parts.length==2){
                float fs=Math.min(20,(b-t)*.28f);
                txt(c,parts[0],(l+r)/2,t+(b-t)*.34f,fs,color);
                txt(c,parts[1],(l+r)/2,t+(b-t)*.70f,fs,color);
            } else txt(c,label,(l+r)/2,(t+b)/2,Math.min(22,(b-t)*.42f),color);
        }
    }

    private void keyWithBackground(Canvas c,float l,float t,float r,float b,String label,int textColor,int backgroundColor,boolean square){
        p.setColor(backgroundColor); p.setStyle(Paint.Style.FILL);
        float rad=square?3:7; c.drawRoundRect(l,t,r,b,rad,rad,p);
        p.setColor(Color.rgb(205,204,199)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1);
        c.drawRoundRect(l,t,r,b,rad,rad,p); p.setStyle(Paint.Style.FILL);
        drawPressEffectIfNeeded(c,l,t,r,b,rad);
        if(label!=null&&!label.isEmpty()) txt(c,label,(l+r)/2,(t+b)/2,Math.min(22,(b-t)*.42f),textColor);
    }

    private void drawPressEffectIfNeeded(Canvas c,float l,float t,float r,float b,float rad){
        if(!pressGlow) return;
        // Use the actual pressed rectangle, not the whole row.
        if(Math.abs(pressL-l)>1.5f || Math.abs(pressT-t)>1.5f ||
           Math.abs(pressR-r)>1.5f || Math.abs(pressB-b)>1.5f) return;
        glowPaint.setShadowLayer(dp(12),0f,0f,Color.YELLOW);
        c.drawRoundRect(new RectF(l,t,r,b),rad,rad,glowPaint);
        c.drawRoundRect(l,t,r,b,rad,rad,pressedPaint);
    }

    private void magnifierIcon(Canvas c,float cx,float cy,float size,boolean active){
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2,size*.10f));
        p.setColor(active?GREEN:NAVY);
        c.drawCircle(cx-size*.10f,cy-size*.10f,size*.25f,p);
        c.drawLine(cx+size*.08f,cy+size*.08f,cx+size*.30f,cy+size*.30f,p);
        p.setStyle(Paint.Style.FILL);
    }

    private boolean drawerOpen = false;

    private void showDrawer() {
        final LinearLayout panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(18, 12, 18, 12);

        TextView title = new TextView(service);
        title.setText("امکانات");
        title.setTextSize(20);
        title.setTextColor(BLACK);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, 54));

        ScrollView scroll = new ScrollView(service);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(service);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button transparency = drawerButton("شفافیت کیبورد");
        Button palette = drawerButton("رنگ کیبورد");
        Button steering = drawerButton("فرمان ماشین");
        Button arabic = drawerButton("حرکت‌ها و صداهای عربی");
        Button history = drawerButton("تاریخچه کلیپ‌بورد (۱۰۰)");
        Button mouse = drawerButton("موس صفحه وب");
        Button calculator = drawerButton("ماشین حساب");

        Button quickSettings = drawerButton("Quick Settings");
        Button microphone = drawerButton("MIC");
        Button[] buttons={transparency,palette,steering,arabic,history,mouse,calculator,quickSettings,microphone};
        for(Button b:buttons) list.addView(b);

        final PopupWindow popup = new PopupWindow(panel,
                Math.min((int)(Math.max(320,getWidth()) * 0.94f), 700),
                Math.min(dp(520), Math.max(dp(260), getHeight() - dp(12))), false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setOutsideTouchable(true);
        popup.setTouchable(true);
        popup.setFocusable(false); // Do not steal focus from the editor / close the IME.
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popup.setElevation(8f);

        transparency.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showTransparency));
        palette.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showColorPalette));
        steering.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showSteeringWheel));
        arabic.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showArabicHarakat));
        history.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showClipboardHistory));
        mouse.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showMouseControls));
        calculator.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showCalculator));
        quickSettings.setOnClickListener(v -> { popup.dismiss(); service.requestQuickSettingsTiles(); });
        microphone.setOnClickListener(v -> { popup.dismiss(); service.voiceSearch(englishMode?"en-US":"fa-IR"); });

        popup.showAtLocation(this, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(6));
        drawerOpen = true;
        popup.setOnDismissListener(() -> drawerOpen = false);
    }





    private void showCalculator() {
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10), dp(10), dp(10), dp(10));
        root.setBackgroundColor(Color.WHITE);

        TextView display = new TextView(service);
        display.setText("0");
        display.setTextSize(26);
        display.setTextColor(BLACK);
        display.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        display.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable dg = new GradientDrawable();
        dg.setColor(Color.rgb(245,245,242));
        dg.setStroke(1, Color.LTGRAY);
        dg.setCornerRadius(dp(6));
        display.setBackground(dg);
        root.addView(display, new LinearLayout.LayoutParams(-1, dp(52)));

        final double[] stored = {0};
        final char[] operation = {' '};
        final boolean[] entering = {false};

        String[][] keys = {{"C","⌫","÷","×"},{"7","8","9","-"},{"4","5","6","+"},{"1","2","3","="},{"0",".","", ""}};
        for (String[] row : keys) {
            LinearLayout line = new LinearLayout(service);
            line.setOrientation(LinearLayout.HORIZONTAL);
            for (String k : row) {
                if (k.isEmpty()) {
                    line.addView(new Space(service), new LinearLayout.LayoutParams(0, dp(48), 1));
                    continue;
                }
                Button btn = new Button(service);
                btn.setText(k);
                btn.setTextSize(17);
                btn.setTextColor(NAVY);
                btn.setAllCaps(false);
                btn.setPadding(0, 0, 0, 0);
                btn.setOnClickListener(v -> {
                    String z = ((Button)v).getText().toString();
                    String cur = display.getText().toString();
                    if (z.equals("C")) {
                        stored[0] = 0; operation[0] = ' '; entering[0] = false;
                        display.setText("0");
                        return;
                    }
                    if (z.equals("⌫")) {
                        if (cur.length() > 1) display.setText(cur.substring(0, cur.length()-1));
                        else display.setText("0");
                        return;
                    }
                    if (z.equals(".") && cur.contains(".")) return;
                    if (z.equals("+") || z.equals("-") || z.equals("×") || z.equals("÷")) {
                        try { stored[0] = Double.parseDouble(cur); } catch (Exception ex) { stored[0] = 0; }
                        operation[0] = z.charAt(0);
                        entering[0] = true;
                        return;
                    }
                    if (z.equals("=")) {
                        if (operation[0] == ' ') return;
                        try {
                            double right = Double.parseDouble(cur);
                            double result;
                            switch (operation[0]) {
                                case '+': result = stored[0] + right; break;
                                case '-': result = stored[0] - right; break;
                                case '×': result = stored[0] * right; break;
                                case '÷': if (right == 0) throw new ArithmeticException(); result = stored[0] / right; break;
                                default: return;
                            }
                            String out = (result == Math.rint(result)) ? Long.toString((long)result) : Double.toString(result);
                            display.setText(out);
                            stored[0] = result;
                            operation[0] = ' ';
                            entering[0] = true;
                        } catch (Exception ex) {
                            display.setText("خطا");
                            stored[0] = 0; operation[0] = ' '; entering[0] = false;
                        }
                        return;
                    }
                    if (entering[0] || cur.equals("خطا")) {
                        display.setText(z.equals(".") ? "0." : z);
                        entering[0] = false;
                    } else {
                        display.setText(cur.equals("0") ? z : cur + z);
                    }
                });
                line.addView(btn, new LinearLayout.LayoutParams(0, dp(48), 1));
            }
            root.addView(line, new LinearLayout.LayoutParams(-1, dp(48)));
        }

        final PopupWindow calc = new PopupWindow(root, dp(320), WindowManager.LayoutParams.WRAP_CONTENT, true);
        calc.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        calc.setOutsideTouchable(true);
        calc.setElevation(dp(8));
        Button close = new Button(service);
        close.setText("بستن");
        close.setTextColor(NAVY);
        close.setAllCaps(false);
        close.setOnClickListener(v -> calc.dismiss());
        root.addView(close, new LinearLayout.LayoutParams(-1, dp(44)));
        calc.showAtLocation(this, Gravity.CENTER, 0, 0);
    }

    private void showAndKeepKeyboard(PopupWindow popup, Runnable action) {
        popup.dismiss();
        postDelayed(action, 15);
    }

    private Button drawerButton(String text) {
        Button b = new Button(service);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(NAVY);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58);
        lp.setMargins(0, 3, 0, 3);
        b.setLayoutParams(lp);
        return b;
    }

    private LinearLayout addPopupHeader(LinearLayout root, String titleText, Button[] closeHolder) {
        LinearLayout bar = new LinearLayout(service);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(2, 0, 2, 0);
        Button close = drawerButton("×"); close.setTextSize(19);
        Button space = drawerButton("Space"); space.setTextSize(13);
        Button back = drawerButton("Backspace"); back.setTextSize(12);
        styleHeaderButton(close, Color.rgb(205, 45, 45), Color.WHITE);
        styleHeaderButton(space, BLUE, Color.WHITE);
        styleHeaderButton(back, BLUE, Color.WHITE);
        TextView title = new TextView(service);
        title.setText(titleText); title.setTextSize(16); title.setTextColor(NAVY); title.setGravity(Gravity.CENTER);
        bar.addView(close, new LinearLayout.LayoutParams(dp(42), dp(36)));
        bar.addView(space, new LinearLayout.LayoutParams(dp(70), dp(36)));
        bar.addView(back, new LinearLayout.LayoutParams(dp(88), dp(36)));
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(36), 1f));
        space.setOnClickListener(v -> service.type(" "));
        back.setOnClickListener(v -> service.backspace());
        if (closeHolder != null && closeHolder.length > 0) closeHolder[0] = close;
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(40)));
        return bar;
    }

    private void styleHeaderButton(Button b, int background, int foreground) {
        b.setTextColor(foreground);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(background);
        gd.setCornerRadius(dp(6));
        b.setBackground(gd);
        b.setPadding(0, 0, 0, 0);
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private void showClipboardHistory() {
        final List<String> items = service.getClipboardHistory();
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 8, 12, 8);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "تاریخچه کلیپ‌بورد — ۱۰۰ مورد آخر", headerClose);
        ScrollView scroll = new ScrollView(service);
        LinearLayout list = new LinearLayout(service); list.setOrientation(LinearLayout.VERTICAL);
        if (items.isEmpty()) {
            TextView empty = new TextView(service); empty.setText("هنوز موردی در تاریخچه نیست"); empty.setGravity(Gravity.CENTER); empty.setTextSize(17);
            list.addView(empty, new LinearLayout.LayoutParams(-1, 80));
        } else {
            for (int i=0;i<items.size();i++) {
                final String item=items.get(i);
                Button b=drawerButton((i+1)+"  "+item.replace("\n"," "));
                b.setOnClickListener(v -> {
                    service.pasteHistory(item);
                });
                list.addView(b);
            }
        }
        scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        final PopupWindow popup=new PopupWindow(root, Math.min(dp(380), Math.max(dp(300), getWidth()-dp(16))), Math.min(dp(620), Math.max(dp(360), getHeight()-dp(16))), false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v -> popup.dismiss());
        popup.showAtLocation(this, Gravity.CENTER, 0, 0);
    }

    private void showColorPalette() {
        final int[] colors = {
                Color.rgb(250,249,244), Color.rgb(255,255,255), Color.rgb(245,245,245),
                Color.rgb(255,244,230), Color.rgb(255,235,235), Color.rgb(235,245,255),
                Color.rgb(235,250,240), Color.rgb(245,238,255), Color.rgb(255,248,205),
                Color.rgb(225,240,235), Color.rgb(235,235,225), Color.rgb(225,230,240)
        };
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(14,8,14,8);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "رنگ کیبورد", headerClose);
        GridLayout grid=new GridLayout(service); grid.setColumnCount(4);
        for(int color:colors){
            Button b=new Button(service); b.setText(""); b.setBackgroundColor(color);
            b.setOnClickListener(v->{ KEY=color; service.getSharedPreferences("fast_keyboard_settings",0).edit().putInt("keyboard_key_color",color).apply(); invalidate(); });
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=70; lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(4,4,4,4); grid.addView(b,lp);
        }
        root.addView(grid,new LinearLayout.LayoutParams(-1,250));
        PopupWindow popup=new PopupWindow(root,Math.min(dp(380),Math.max(dp(300),getWidth()-dp(16))),Math.min(dp(430),Math.max(dp(330),getHeight()-dp(16))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private void showTransparency() {
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,10,28,10);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "شفافیت کیبورد", headerClose);
        SeekBar bar=new SeekBar(service); bar.setMax(99); int current=Math.max(1,Math.min(100,Math.round(getAlpha()*100f))); bar.setProgress(current-1); root.addView(bar,new LinearLayout.LayoutParams(-1,56));
        TextView value=new TextView(service); value.setText(current+"%"); value.setTextSize(17); value.setTextColor(BLACK); value.setGravity(Gravity.CENTER); root.addView(value,new LinearLayout.LayoutParams(-1,48));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar b,int progress,boolean fromUser){int v=progress+1; value.setText(v+"%"); setAlpha(v/100f); service.getSharedPreferences("fast_keyboard_settings",0).edit().putInt("keyboard_alpha",v).apply();} public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){} });
        PopupWindow popup=new PopupWindow(root,Math.min(dp(380),Math.max(dp(300),getWidth()-dp(16))),Math.min(dp(300),Math.max(dp(260),getHeight()-dp(16))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private void showEmojiPicker() {
        final String[] emojis = {
                "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😝","😜","🤪","🤨","🧐","🤓","😎","🤩","🥳","😏","😒","😞","😔","😟","😕","🙁","☹️","😣","😖","😫","😩","🥺","😢","😭","😤","😠","😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗","🤔","🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧","😮","😲","🥱","😴","🤤","😪","😵","🤐","🥴","🤢","🤮","🤧","😷","🤒","🤕","🤠","🥸","😈","👿","👹","👺","💀","☠️","👻","👽","🤖","🎃","😺","😸","😹","😻","😼","😽","🙀","😿","😾",
                "❤️","🩷","🧡","💛","💚","🩵","💙","💜","🤎","🖤","🩶","🤍","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","💯","💢","💥","💦","💨","💫","💬","🗨️","🗯️","💭","💤",
                "👍","👎","👏","🙌","🙏","🤝","👌","✌️","🤞","🤟","🤘","🤙","👋","💪","👊","✊","👉","👈","☝️","👇","👆","✍️","💅","🫶","🤲","🙋",
                "🔥","⭐","✨","🎉","🎊","✅","❌","⚡","🌹","🌸","🌺","🌻","🌼","🌷","🌱","🌿","🍀","☘️","🍁","🍂","🍃","🌞","🌝","🌚","🌙","🌟","💫","☀️","🌈","☁️","❄️","☃️","🌊",
                "🍎","🍏","🍊","🍋","🍌","🍉","🍇","🍓","🫐","🍒","🍑","🍍","🥝","🥑","🍅","🥕","🌽","🍞","🧀","🍔","🍟","🍕","🌭","🍿","🍩","🍪","🍰","🎂","🍫","🍭","🍬","🍯","🥭","🥥","🥨","🍗","🍖","🌮","🌯","🍜","🍣","🍱","🥗","🍦","☕","🍵","🧃","🥤","🧋",
                "⚽","🏀","🏈","⚾","🎾","🏐","🎱","🏆","🥇","🥈","🥉","🎯","🎮","🎲","🧩","🎸","🎹","🎺","🥁","🎻","🎬","🎨","🎈","🎁","🎀","🎟️","🎫",
                "🚗","🚕","🚙","🚌","🚓","🚑","🚒","🚜","🚲","🛵","🏍️","✈️","🚀","🚢","⛵","🚉","🚇","🚦","🛑","🏠","🏢","🏫","🏥","🏰","🗽","⛺",
                "⌚","📱","💻","🖥️","⌨️","🖨️","📷","🎥","🎧","🎤","📚","✏️","📝","📌","📎","🔒","🔑","💡","🔔","⚙️","🔍","🔎","🔧","🔨","🪛","🧰","🔩","🧲","🔬","🔭","💊","🩺"
        };
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(8,8,8,8);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "انتخاب Emoji", headerClose);
        ScrollView scroll=new ScrollView(service);
        LinearLayout all=new LinearLayout(service); all.setOrientation(LinearLayout.VERTICAL);
        GridLayout grid=new GridLayout(service); grid.setColumnCount(8); grid.setPadding(6,6,6,6);
        all.addView(grid, new LinearLayout.LayoutParams(-1,-2));
        TextView countryTitle=new TextView(service); countryTitle.setText("پرچم کشورها"); countryTitle.setTextColor(NAVY); countryTitle.setTextSize(15); countryTitle.setGravity(Gravity.CENTER);
        all.addView(countryTitle,new LinearLayout.LayoutParams(-1,dp(34)));
        GridLayout countryGrid=new GridLayout(service); countryGrid.setColumnCount(8); countryGrid.setPadding(4,2,4,2);
        all.addView(countryGrid,new LinearLayout.LayoutParams(-1,-2));
        scroll.addView(all,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        final PopupWindow popup=new PopupWindow(root,Math.min((int)(getWidth()*0.96f),720),Math.min(dp(620),Math.max(dp(380),getHeight()-dp(10))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss());
        popup.showAtLocation(this,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,dp(6));
        root.post(() -> { for(String emoji:emojis) addEmojiButton(grid, emoji); addCountryFlags(countryGrid); });
    }

    private void addCountryFlags(GridLayout grid) {
        String[] countries={"AF","AL","DZ","AS","AD","AO","AI","AQ","AG","AR","AM","AW","AU","AT","AZ","BS","BH","BD","BB","BY","BE","BZ","BJ","BM","BT","BO","BQ","BA","BW","BV","BR","IO","BN","BG","BF","BI","CV","KH","CM","CA","KY","CF","TD","CL","CN","CX","CC","CO","KM","CG","CD","CK","CR","CI","HR","CU","CW","CY","CZ","DK","DJ","DM","DO","EC","EG","SV","GQ","ER","EE","SZ","ET","FK","FO","FJ","FI","FR","GF","PF","TF","GA","GM","GE","DE","GH","GI","GR","GL","GD","GP","GU","GT","GG","GN","GW","GY","HT","HN","HK","HU","IS","IN","ID","IR","IQ","IE","IM","IL","IT","JM","JP","JE","JO","KZ","KE","KI","KP","KR","KW","KG","LA","LV","LB","LS","LR","LY","LI","LT","LU","MO","MG","MW","MY","MV","ML","MT","MH","MQ","MR","MU","YT","MX","FM","MD","MC","MN","ME","MS","MA","MZ","MM","NA","NR","NP","NL","NC","NZ","NI","NE","NG","NU","NF","MK","MP","NO","OM","PK","PW","PS","PA","PG","PY","PE","PH","PN","PL","PT","PR","QA","RE","RO","RU","RW","BL","SH","KN","LC","MF","PM","VC","WS","SM","ST","SA","SN","RS","SC","SL","SG","SX","SK","SI","SB","SO","ZA","GS","SS","ES","LK","SD","SR","SJ","SE","CH","SY","TW","TJ","TZ","TH","TL","TG","TK","TO","TT","TN","TR","TM","TC","TV","UG","UA","AE","GB","US","UM","UY","UZ","VU","VE","VN","VG","VI","WF","EH","YE","ZM","ZW"};
        for(String code:countries) addEmojiButton(grid,flagFromCode(code));
    }

    private void addColoredFolderButton(GridLayout grid, int color) {
        final FolderEmojiView folder = new FolderEmojiView(service, color);
        GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(56); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(2,2,2,2); grid.addView(folder,lp);
        folder.setOnClickListener(v -> service.typeUnit("📁"));
    }

    private class FolderEmojiView extends View {
        private final Paint fp=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int color;
        FolderEmojiView(Context c,int color){super(c);this.color=color;setContentDescription("📁");}
    }

    private float[] visibleRowBounds(){
        // Resize is an in-view operation. The view remains the normal IME height and
        // the keyboard is always anchored to its bottom edge. When Resize is open,
        // the roller occupies the top strip and the keyboard uses the remaining
        // space, so no content is pushed below the device navigation bar.
        float viewH = Math.max(1f, getHeight());
        if(!resizeRollerOpen){
            return new float[]{0f,0.162f,0.262f,0.424f,0.586f,0.748f,0.8875f,1f};
        }
        // Reserve the roller strip at the top. The keyboard height is controlled
        // only from its top edge; its bottom is always exactly at viewH.
        float roller = Math.min(dp(RESIZE_ROLLER_DP), viewH * 0.22f);
        float available = Math.max(1f, viewH - roller);
        float normalized = (keyboardScale - 0.70f) / 0.30f;
        normalized = Math.max(0f, Math.min(1f, normalized));
        float keyboardH = Math.max(viewH * 0.55f, available * (0.70f + 0.30f * normalized));
        keyboardH = Math.min(available, keyboardH);
        float keyboardTopPx = viewH - keyboardH;
        // The roller stays immediately above the keyboard. If the resized keyboard
        // is shorter, the unused area remains above it, never below it.
        float[] rows={0f,0.162f,0.262f,0.424f,0.586f,0.748f,0.8875f,1f};
        for(int i=0;i<rows.length;i++) rows[i]=(keyboardTopPx+rows[i]*keyboardH)/viewH;
        return rows;
    }

    private void drawKeyboard(Canvas c){
        drawRowsAsRealKeys(c,visibleRowBounds());
    }

    private void drawRowsAsRealKeys(Canvas c,float[] b){
        float h=getHeight();
        float y0=b[0]*h,y1=b[1]*h,y2=b[2]*h,y3=b[3]*h,y4=b[4]*h,y5=b[5]*h,y6=b[6]*h,y7=b[7]*h;
        drawTopToolbar(c,y0,y1);
        drawSevenPartRow(c,y1,y2);
        drawNumberRow(c,y2,y3);
        drawPersianRow(c,y3,y4,true);
        drawPersianRow(c,y4,y5,false);
        float eg=dp(4), er=getWidth()-eg, enterW=(er-eg)*.115f, enL=er-enterW;
        keyWithBackground(c,enL,y3,er,y5,"Enter",NAVY,ENTER_BG,false);
        drawPersianThirdRow(c,y5,y6);
        drawBottomRow(c,y6,y7);
    }

    private void drawTopToolbar(Canvas c,float top,float bottom){
        float[] weights={0.100f,0.100f,0.090f,0.080f,0.080f,0.080f,0.090f,0.100f,0.100f};
        float g=dp(4),total=0f;for(float q:weights)total+=q;
        // Resize button is exactly one alphabet-key width.
        float alphaEnter=(getWidth()-g*2)*.115f;
        float alphaGap=dp(4);
        float alphaW=(getWidth()-alphaGap*2-alphaEnter-alphaGap*10)/11f;
        float fixedTotal=0f;for(float q:weights)fixedTotal+=q;
        float normalScale=(getWidth()-g*(weights.length+2)-alphaW)/fixedTotal;
        float scale=normalScale,x=g;
        String[] labels={"Copy\nAll","Copy\nScreen","Paste","Cut","Undo","Redo","100\nHistory","امکانات","Mouse"};
        for(int i=0;i<labels.length;i++){
            float cw=weights[i]*scale;
            if(i==8){
                key(c,x,top,x+cw,bottom,"",NAVY,false);
                drawMousePointer(c,x,top,x+cw,bottom);
            } else if(i==0||i==1||i==6)drawTwoLineKeyBold(c,x,top,x+cw,bottom,labels[i],NAVY);
            else key(c,x,top,x+cw,bottom,labels[i],NAVY,false);
            x+=cw+g;
        }
        // Resize button: alphabet-key width, no roller drawn on the button.
        key(c,x,top,x+alphaW,bottom,"",NAVY,false);
        txtBold(c,"Resize",x+alphaW/2,(top+bottom)/2,Math.min(18,(bottom-top)*.30f),NAVY);

        if(resizeRollerOpen){
            // The roller is a separate strip inside the fixed IME view, immediately
            // above the keyboard. The keyboard bottom never moves.
            float fullTop=0f;
            float rollerBottom=top-dp(4);
            float rollerH=Math.min(dp(RESIZE_ROLLER_DP), getHeight()*0.22f);
            float rollerTop=Math.max(fullTop,rollerBottom-rollerH);
            float rl=dp(18), rr=getWidth()-dp(18), cy=(rollerTop+rollerBottom)/2f;
            key(c,rl-dp(8),rollerTop,rr+dp(8),rollerBottom,"",NAVY,false);
            p.setColor(Color.rgb(210,210,205)); p.setStrokeWidth(dp(7)); p.setStrokeCap(Paint.Cap.ROUND);
            c.drawLine(rl,cy,rr,cy,p);
            float knob=rl+(rr-rl)*(keyboardScale-0.70f)/0.30f;
            p.setColor(Color.rgb(45,125,225)); c.drawLine(rl,cy,knob,cy,p);
            c.drawCircle(knob,cy,dp(12),p);
            txtBold(c,"−",rl,cy,20,NAVY);
            txtBold(c,"+",rr,cy,20,NAVY);
        }
    }

    private int resizeToolbarIndex(float x){
        float[] weights={0.100f,0.100f,0.090f,0.080f,0.080f,0.080f,0.090f,0.100f,0.100f};
        float g=dp(4),alphaEnter=(getWidth()-g*2)*.115f,alphaW=(getWidth()-g*2-alphaEnter-g*10)/11f;
        float total=0f;for(float q:weights)total+=q;
        float scale=(getWidth()-g*(weights.length+2)-alphaW)/total,pos=g;
        for(int i=0;i<weights.length;i++){float cw=weights[i]*scale;if(x>=pos&&x<=pos+cw)return i;pos+=cw+g;}
        if(x>=pos&&x<=pos+alphaW)return 9;
        return -1;
    }

    private boolean touchResizeRoller(float x,float y){
        if(!resizeRollerOpen)return false;
        float[] b=visibleRowBounds();
        float top=b[0]*getHeight();
        float rollerBottom=top-dp(4);
        float rollerH=Math.min(dp(RESIZE_ROLLER_DP), getHeight()*0.22f);
        float rollerTop=Math.max(0f,rollerBottom-rollerH);
        float rl=dp(18), rr=getWidth()-dp(18), cy=(rollerTop+rollerBottom)/2f;
        if(x>=rl-dp(14)&&x<=rr+dp(14)&&y>=rollerTop-dp(10)&&y<=rollerBottom+dp(10)){
            float q=(x-rl)/(rr-rl);
            keyboardScale=0.70f+Math.max(0f,Math.min(1f,q))*0.30f;
            service.setKeyboardResizeMode(true, keyboardScale);
            invalidate(); return true;
        }
        return false;
    }

    private void drawSevenPartRow(Canvas c,float top,float bottom){
        float g=dp(4), left=g, right=getWidth()-g;
        float cw=(right-left-g*6f)/7f;
        for(int i=0;i<7;i++){
            float l=left+i*(cw+g);
            key(c,l,top,l+cw,bottom,"",NAVY,false);
            if(i<suggestions.length && suggestions[i]!=null && !suggestions[i].isEmpty()){
                txt(c,suggestions[i],l+cw/2f,(top+bottom)/2f,Math.min(17f,(bottom-top)*.34f),NAVY);
            }
        }
    }

    private void drawNumberRow(Canvas c,float top,float bottom){
        float g=dp(4),left=g,right=getWidth()-g,backW=(right-left)*.145f,normalArea=right-left-backW-g,cw=(normalArea-g*9)/10f;
        String[] nums={"1","2","3","4","5","6","7","8","9","0"};
        String[] symbols={"!","@","#","$","%","^","&","*","(",")"};
        for(int i=0;i<10;i++){
            float l=left+i*(cw+g);
            key(c,l,top,l+cw,bottom,"",NAVY,false);
            float small=Math.min(17f,(bottom-top)*.24f);
            txtBold(c,symbols[i],l+cw*.82f,top+(bottom-top)*.20f,Math.min(24f,(bottom-top)*.31f),SYMBOL_RED);
            txtBold(c,nums[i],l+cw/2f,top+(bottom-top)*.58f,Math.min(31f,(bottom-top)*.52f),NUMBER_BROWN);
        }
        float bl=left+10*(cw+g)+g;keyWithBackground(c,bl,top,right,bottom,"⌫",NAVY,BACKSPACE_BG,false);
    }

    private void drawPersianRow(Canvas c,float top,float bottom,boolean first){
        float g=dp(4),left=g,right=getWidth()-g,enterW=(right-left)*.115f,normalRight=right-enterW-g;
        String[] keys=englishMode?(first?new String[]{"Q","W","E","R","T","Y","U","I","O","P","["}:new String[]{"A","S","D","F","G","H","J","K","L",";","'"}):(first?new String[]{"ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج"}:new String[]{"ش","س","ی","ب","ل","ا","ت","ن","م","ک","گ"});
        float cw=(normalRight-left-g*(keys.length-1))/keys.length;
        for(int i=0;i<keys.length;i++){float l=left+i*(cw+g);key(c,l,top,l+cw,bottom,"",NAVY,false);txtAlphabet(c,keys[i],l+cw/2f,(top+bottom)/2f,Math.min(31f,(bottom-top)*.54f),NAVY);}
    }

    private void drawPersianThirdRow(Canvas c,float top,float bottom){
        float g=dp(4),left=g,right=getWidth()-g,total=right-left;
        float eqW=total*.105f;
        float oldCapsW=(total-eqW-g*11f)/11f;
        String[] keys=englishMode?new String[]{"Caps","Z","X","C","V","B","N","M",",",".","/"}:new String[]{"Caps","ظ","ط","ژ","ز","ر","ذ","د","پ","و","چ"};
        float x=left;
        for(int i=0;i<keys.length;i++){
            float w=(i==0)?eqW:oldCapsW;
            float l=x,r=l+w;
            if(i==0 && (capsMode!=0 || capsHold)){
                keyWithBackground(c,l,top,r,bottom,"",Color.WHITE,GREEN,false);
                txtBold(c,"Caps",(l+r)/2f,(top+bottom)/2f,Math.min(22f,(bottom-top)*.38f),Color.WHITE);
            } else {
                key(c,l,top,r,bottom,"",NAVY,false);
                txtAlphabet(c,keys[i],(l+r)/2f,(top+bottom)/2f,Math.min(31f,(bottom-top)*.54f),NAVY);
            }
            x=r+g;
        }
        drawTwoLineKeyBold(c,x,top,right,bottom,"=\n_",SYMBOL_RED);
    }

    private void drawBottomRow(Canvas c,float top,float bottom){
        float g=dp(4);
        float[] weights={0.065f,0.095f,0.090f,0.205f,0.060f,0.060f,0.070f,0.110f,0.110f,0.060f,0.060f};
        float total=0f; for(float q:weights) total+=q;
        float scale=(getWidth()-g*(weights.length+1))/total, x=g;
        String[] labels={"اموجی","123\n!#@...","🌐","Space","،","؟","+\n-","←","→","↑","↓"};
        for(int i=0;i<labels.length;i++){
            float cw=weights[i]*scale;
            float l=x,r=x+cw;
            int bg=(i==3)?SPACE_BG:DEFAULT_KEY;
            if(i==6){
                keyWithBackground(c,l,top,r,bottom,"",NAVY,bg,false);
                drawPlusMinus(c,l,top,r,bottom);
            } else if(i>=7){
                keyWithBackground(c,l,top,r,bottom,"",NAVY,bg,false);
                drawArrow(c,l,top,cw,bottom-top,labels[i]);
            } else if(i==1){
                keyWithBackground(c,l,top,r,bottom,"",NAVY,bg,false);
                draw123(c,l,top,r,bottom);
            } else {
                keyWithBackground(c,l,top,r,bottom,labels[i],(i==4||i==5)?SYMBOL_RED:NAVY,bg,false);
            }
            x=r+g;
        }
    }

    private void draw123(Canvas c,float l,float t,float r,float b){
        float cy=t+(b-t)*0.34f;
        txtBold(c,"123",(l+r)/2f,cy,Math.min(17f,(b-t)*0.25f),NAVY);
        txtBold(c,"!#@...",(l+r)/2f,t+(b-t)*0.70f,Math.min(14f,(b-t)*0.21f),NAVY);
    }

    private void drawPlusMinus(Canvas c,float l,float t,float r,float b){
        txtBold(c,"+",(l+r)/2f,t+(b-t)*0.33f,Math.min(21f,(b-t)*0.30f),SYMBOL_RED);
        txtBold(c,"-",(l+r)/2f,t+(b-t)*0.70f,Math.min(19f,(b-t)*0.27f),SYMBOL_RED);
    }
    private void drawTwoLineKeyBold(Canvas c,float l,float t,float r,float b,String label,int textColor){
        key(c,l,t,r,b,"",textColor,false);
        String[] a=label.split("\\n",-1);
        if(a.length==2){ float fs=Math.min(20,(b-t)*.28f); txtBold(c,a[0],(l+r)/2,t+(b-t)*.34f,fs,textColor); txtBold(c,a[1],(l+r)/2,t+(b-t)*.70f,fs,textColor); }
        else txtBold(c,label,(l+r)/2,(t+b)/2,Math.min(20,(b-t)*.35f),textColor);
    }

    private void drawTwoLineKey(Canvas c,float l,float t,float r,float b,String label,int textColor){
        key(c,l,t,r,b,"",textColor,false);
        String[] a=label.split("\\n",-1);
        if(a.length==2){ txt(c,a[0],(l+r)/2,t+(b-t)*.34f,Math.min(20,(b-t)*.28f),textColor); txt(c,a[1],(l+r)/2,t+(b-t)*.69f,Math.min(20,(b-t)*.28f),textColor); }
        else txt(c,label,(l+r)/2,(t+b)/2,Math.min(20,(b-t)*.35f),NAVY);
    }

    private void drawEnglishRows(Canvas c,float[] bounds){
        String[][] rows={
            {"1","2","3","4","5","6","7","8","9","0","-","="},
            {"Q","W","E","R","T","Y","U","I","O","P","[","]","\\"},
            {"Caps","A","S","D","F","G","H","J","K","L",";","'",""},
            {"Z","X","C","V","B","N","M",",",".","/","?","",""}
        };
        int[] logical={2,3,4,5};
        for(int z=0;z<logical.length;z++){
            int row=logical[z];
            float y0=bounds[row]*getHeight(), y1=bounds[row+1]*getHeight();
            float left=dp(4), right=getWidth()-dp(4);
            boolean reserveRight=(row==2 || row==3 || row==4);
            if(reserveRight) right=getWidth()*.914f;
            int count=rows[z].length;
            float ww=(right-left-dp(4)*(count-1))/count;
            for(int i=0;i<count;i++){
                float l=left+i*(ww+dp(4));
                key(c,l,y0,l+ww,y1,rows[z][i],NAVY,false);
            }
            if(row==3 || row==4){
                float enterL=getWidth()*.914f;
                keyWithBackground(c,enterL,y0,getWidth()-dp(4),y1,"Enter",NAVY,ENTER_BG,false);
            }
        }
    }

    private void drawCapsAndMagnifierRow(Canvas c,float y){}

    private void drawMicrophone(Canvas c,float l,float t,float r,float b){
        float cx=(l+r)/2f, cy=(t+b)/2f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3f,keyH*.09f)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(NAVY);
        c.drawRoundRect(cx-keyH*.13f, cy-keyH*.28f, cx+keyH*.13f, cy+keyH*.10f, keyH*.13f, keyH*.13f, p);
        c.drawArc(cx-keyH*.25f, cy-keyH*.10f, cx+keyH*.25f, cy+keyH*.32f, 0, 180, false, p);
        c.drawLine(cx, cy+keyH*.30f, cx, cy+keyH*.43f, p);
        c.drawLine(cx-keyH*.15f, cy+keyH*.43f, cx+keyH*.15f, cy+keyH*.43f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawMousePointer(Canvas c,float l,float t,float r,float b){
        float cx=l+(r-l)*.50f;
        float cy=t+(b-t)*.50f;
        float s=Math.min(r-l,b-t)*.24f;
        Path pointer=new Path();
        pointer.moveTo(cx-s*.65f, cy-s);
        pointer.lineTo(cx-s*.65f, cy+s*.72f);
        pointer.lineTo(cx-s*.08f, cy+s*.30f);
        pointer.lineTo(cx+s*.22f, cy+s*.92f);
        pointer.lineTo(cx+s*.50f, cy+s*.72f);
        pointer.lineTo(cx+s*.20f, cy+s*.12f);
        pointer.lineTo(cx+s*.82f, cy+s*.12f);
        pointer.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(NAVY);
        c.drawPath(pointer,p);
    }

    private void row(Canvas c,float y,float[] weights,String[] labels){
        float total=0;
        for(float q:weights)total+=q;
        float ww=(getWidth()-gap*(weights.length+1))/total,x=gap;
        for(int i=0;i<weights.length;i++){
            float cw=ww*weights[i];
            String s=labels[i];
            if(s.contains("\n")){
                key(c,x,y,x+cw,y+keyH,"",NAVY,false);
                String[] a=s.split("\\n");
                txt(c,a[0],x+cw/2,y+keyH*.35f,Math.min(20,keyH*.3f),NAVY);
                txt(c,a[1],x+cw/2,y+keyH*.7f,Math.min(20,keyH*.3f),NAVY);
            }else{
                key(c,x,y,x+cw,y+keyH,s,NAVY,false);
            }
            x+=cw+gap;
        }
    }

    private void rowFrom(Canvas c,float y,float left,String[] labels){
        float x=left+gap,available=getWidth()-left-gap;
        float ww=(available-gap*(labels.length+1))/labels.length;
        for(String s:labels){
            key(c,x,y,x+ww,y+keyH,s,BLUE,false);
            x+=ww+gap;
        }
    }

    private void rowFromRightReserved(Canvas c,float y,float left,String[] labels,float reservedRight){
        float x=left+gap;
        float available=getWidth()-left-reservedRight-gap;
        float ww=(available-gap*(labels.length+1))/labels.length;
        for(String s:labels){
            if(s.contains("\n")){
                key(c,x,y,x+ww,y+keyH,"",BLUE,false);
                String[] a=s.split("\n");
                txt(c,a[0],x+ww/2,y+keyH*.35f,Math.min(20,keyH*.3f),NAVY);
                txt(c,a[1],x+ww/2,y+keyH*.7f,Math.min(20,keyH*.3f),NAVY);
            } else key(c,x,y,x+ww,y+keyH,s,BLUE,false);
            x+=ww+gap;
        }
    }

    private void rowFromRightReservedWithEscape(Canvas c,float y,float left,String[] labels,float reservedRight){
        float x=left+gap;
        float available=getWidth()-left-reservedRight-gap;
        float ww=(available-gap*(labels.length+1))/labels.length;
        for(String s:labels){
            if(s.contains("\n")){
                key(c,x,y,x+ww,y+keyH,"",BLUE,false);
                String[] a=s.split("\n");
                txt(c,a[0],x+ww/2,y+keyH*.35f,Math.min(18,keyH*.28f),NAVY);
                txt(c,a[1],x+ww/2,y+keyH*.70f,Math.min(18,keyH*.28f),NAVY);
            } else {
                // Draw the third alphabet row only once so its glyphs have the same stroke weight as the other alphabet rows.
                key(c,x,y,x+ww,y+keyH,s,BLUE,false);
            }
            x+=ww+gap;
        }
    }

    private void drawArrow(Canvas c,float x,float y,float ww,float hh,String s){
        key(c,x,y,x+ww,y+hh,"",NAVY,true);
        p.setTypeface(Typeface.DEFAULT_BOLD); p.setTextSize(hh*.62f); p.setColor(BLUE); p.setTextAlign(Paint.Align.CENTER);
        p.setStyle(Paint.Style.FILL); c.drawText(s,x+ww/2,y+hh/2-(p.ascent()+p.descent())/2,p);
    }

    private void drawPressGlow(Canvas c){
        // Keep the press light exactly inside the same rounded shape as the key.
        float inset = Math.max(1f, keyH * .018f);
        float l = pressL + inset, t = pressT + inset, r = pressR - inset, b = pressB - inset;
        float radius = Math.max(2f, Math.min(7f, keyH * .025f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(145, 255, 190, 0));
        c.save();
        Path clip = new Path();
        clip.addRoundRect(new RectF(l, t, r, b), radius, radius, Path.Direction.CW);
        c.clipPath(clip);
        c.drawRoundRect(l, t, r, b, radius, radius, p);
        c.restore();
    }

    private String currentWordForSuggestions(){
        InputConnection ic=service.getCurrentInputConnection();
        CharSequence q=ic==null?null:ic.getTextBeforeCursor(120,0);
        if(q==null) return "";
        String b=q.toString(); int i=b.length()-1;
        while(i>=0 && !Character.isWhitespace(b.charAt(i)) && ".,!?؛،:()[]{}\"'«»".indexOf(b.charAt(i))<0) i--;
        return b.substring(i+1);
    }

    public String[] getRelatedSuggestions(){
        String word=currentWordForSuggestions();
        String w=word.trim().toLowerCase(java.util.Locale.ROOT);
        if(w.isEmpty()) return new String[]{"","",""};
        if(w.equals("بیمه") || w.contains("بیمه")) return new String[]{"بیمه درمانی","بیمه تکمیلی","بیمه کار"};
        if(w.equals("درمان") || w.equals("پزشک") || w.equals("دارو")) return new String[]{"بیمه درمانی","بیمه تکمیلی","پرونده درمانی"};
        if(w.equals("مدرسه") || w.equals("دبیرستان") || w.equals("دانشگاه") || w.equals("آموزش")) return new String[]{"دانش آموز","رشته تحصیلی","مرکز آموزشی"};
        if(w.equals("کودک") || w.equals("بچه") || w.contains("مهد")) return new String[]{"نگهداری کودک","مهدکودک","پرستار کودک"};
        if(w.equals("منظومه") || w.equals("سیاره") || w.equals("ستاره") || w.equals("نجوم")) return new String[]{"منظومه شمسی","ستاره شناسی","کهکشان"};
        if(w.equals("ماشین") || w.equals("خودرو")) return new String[]{"موتور خودرو","قطعات خودرو","تعمیرات خودرو"};
        if(w.equals("برنامه") || w.equals("برنامه نویسی") || w.equals("کدنویسی")) return new String[]{"برنامه نویسی","کدنویسی","خطایابی"};
        if(w.equals("جنگ") || w.equals("ww2") || w.equals("wwii")) return new String[]{"جنگ جهانی دوم","جنگنده","نیروی هوایی"};
        return new String[]{"","",""};
    }

    public void refreshSuggestions(){
        String word=currentWordForSuggestions();
        Arrays.fill(suggestions,"");
        LinkedHashSet<String> found=new LinkedHashSet<>();
        for(String[] group:WORDS) for(String candidate:group)
            if(!candidate.equals(word) && candidate.startsWith(word)) found.add(candidate);
        for(String candidate:EXTRA_WORDS){
            if(candidate!=null && !candidate.isEmpty() && !candidate.equals(word) && candidate.startsWith(word)) found.add(candidate);
            if(found.size()>=12) break;
        }
        if(found.size()<4){
            for(String[] group:WORDS) for(String candidate:group)
                if(!candidate.equals(word) && candidate.contains(word)) found.add(candidate);
            for(String candidate:EXTRA_WORDS){
                if(candidate!=null && !candidate.isEmpty() && !candidate.equals(word) && candidate.contains(word)) found.add(candidate);
                if(found.size()>=12) break;
            }
            for(String candidate:TOPIC_WORDS){
                if(!candidate.equals(word) && candidate.contains(word)) found.add(candidate);
                if(found.size()>=12) break;
            }
        }
        // Fill any remaining suggestion slots from the expanded built-in word bank,
        // while keeping the Hidden button separate.
        if(found.size()<6){
            for(String candidate:EXTRA_WORDS){
                if(candidate!=null && !candidate.isEmpty() && !candidate.equals(word)) found.add(candidate);
                if(found.size()>=6) break;
            }
            for(String candidate:TOPIC_WORDS){
                if(!candidate.equals(word)) found.add(candidate);
                if(found.size()>=6) break;
            }
        }
        int n=0; for(String candidate:found){ suggestions[n++]=candidate; if(n>=6) break; }
        invalidate();
        service.updateRelatedSuggestions();
    }

    private void setPressGlow(float l, float t, float r, float b, boolean held){
        pressL=l; pressT=t; pressR=r; pressB=b;
        pressHeld=held;
        pressGlow=true;
        handler.removeCallbacks(clearPressGlow);
        if (!held) handler.postDelayed(clearPressGlow, 140);
        invalidate();
    }

    private void clearPressGlowNow(){
        pressHeld=false;
        pressGlow=false;
        handler.removeCallbacks(clearPressGlow);
        invalidate();
    }

    private int getRowAt(float y){
        if(y<0 || y>getHeight()) return -1;
        float[] b=visibleRowBounds();
        for(int i=0;i<7;i++){
            if((i==0&&hideTopRow)||(i==1&&hideSuggestionRow)) continue;
            if(y>=b[i]*getHeight() && y<=b[i+1]*getHeight()) return i;
        }
        return -1;
    }

    private float rowTop(int row){ return visibleRowBounds()[row]*getHeight(); }

    private void pressRectFor(float x,float y,boolean held){
        RectF rect=findDrawnKeyRect(x,y);
        if(rect==null){ clearPressGlowNow(); return; }
        setPressGlow(rect.left,rect.top,rect.right,rect.bottom,held);
    }

    private RectF findDrawnKeyRect(float x,float y){
        int row=getRowAt(y);
        if(row<0) return null;
        float[] b=visibleRowBounds();
        float top=b[row]*getHeight(), bottom=b[row+1]*getHeight();
        float g=dp(4);
        if(row==0){
            float[] weights={0.100f,0.100f,0.090f,0.080f,0.080f,0.080f,0.090f,0.100f,0.100f,0.180f};
            float total=0f; for(float q:weights) total+=q;
            float scale=(getWidth()-g*(weights.length+1))/total,pos=g;
            for(float q:weights){float cw=q*scale;if(x>=pos&&x<=pos+cw)return new RectF(pos,top,pos+cw,bottom);pos+=cw+g;}
            return null;
        }
        if(row==1){
            float left=g,right=getWidth()-g,cw=(right-left-g*6f)/7f;
            for(int i=0;i<7;i++){float l=left+i*(cw+g);if(x>=l&&x<=l+cw)return new RectF(l,top,l+cw,bottom);}
            return null;
        }
        if(row==2){
            float left=g,right=getWidth()-g,backW=(right-left)*.145f,normalArea=right-left-backW-g,cw=(normalArea-g*9)/10f;
            if(x>=right-backW) return new RectF(right-backW,top,right,bottom);
            for(int i=0;i<10;i++){float l=left+i*(cw+g);if(x>=l&&x<=l+cw)return new RectF(l,top,l+cw,bottom);}
            return null;
        }
        if(row==3 || row==4){
            float left=g,right=getWidth()-g,enterW=(right-left)*.115f,normalRight=right-enterW-g;
            if(x>=normalRight+g) return new RectF(normalRight+g,top,right,bottom);
            return keyRectForEqualCell(x,left,normalRight,11,top,bottom);
        }
        if(row==5){
            float left=g,right=getWidth()-g,total=right-left,eqW=total*.105f;
            float oldCapsW=(total-eqW-g*11f)/11f;
            float pos=left;
            for(int i=0;i<11;i++){ float w=(i==0)?eqW:oldCapsW; if(x>=pos&&x<=pos+w)return new RectF(pos,top,pos+w,bottom); pos+=w+g; }
            if(x>=pos&&x<=right)return new RectF(pos,top,right,bottom);
        }
        if(row==6){
            float[] weights={0.065f,0.095f,0.090f,0.205f,0.060f,0.060f,0.070f,0.110f,0.110f,0.060f,0.060f};
            float total=0f;for(float q:weights)total+=q;float scale=(getWidth()-g*(weights.length+1))/total,pos=g;
            for(float q:weights){float cw=q*scale;if(x>=pos&&x<=pos+cw)return new RectF(pos,top,pos+cw,bottom);pos+=cw+g;}
        }
        return null;
    }

    private RectF keyRectForEqualCell(float x,float left,float right,int count,float top,float bottom){
        float g=dp(4),cw=(right-left-g*(count-1))/count;
        for(int i=0;i<count;i++){float l=left+i*(cw+g);if(x>=l&&x<=l+cw)return new RectF(l,top,l+cw,bottom);}
        return null;
    }

    private boolean isAlifAt(float x, float y){
        int row=getRowAt(y);
        if(row!=4 || englishMode) return false;
        float g=dp(4), left=g, right=getWidth()-g, enterW=(right-left)*0.115f, normalRight=right-enterW-g;
        int index=5; // Persian second row: ش س ی ب ل ا ت ن م ک گ
        float cw=(normalRight-left-g*10f)/11f;
        float l=left+index*(cw+g);
        return x>=l && x<=l+cw;
    }

    private void showAlifArabicPicker(){
        final PopupWindow[] holder=new PopupWindow[1];
        LinearLayout root=new LinearLayout(service);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(6),dp(6),dp(6),dp(6));
        root.setBackgroundColor(Color.rgb(250,249,244));
        String[] variants={"ا","آ","أ","إ","ٱ","ى","ئ"};
        for(String v:variants){
            Button b=new Button(service);
            b.setText(v); b.setTextSize(25); b.setTextColor(NAVY); b.setAllCaps(false);
            b.setMinWidth(0); b.setMinimumWidth(0);
            b.setPadding(dp(8),0,dp(8),0);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(54),dp(58));
            lp.setMargins(dp(2),0,dp(2),0);
            root.addView(b,lp);
            b.setOnClickListener(view -> {
                service.typeUnit(v);
                if(holder[0]!=null) holder[0].dismiss();
            });
        }
        PopupWindow popup=new PopupWindow(root,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,true);
        holder[0]=popup;
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        int[] loc=new int[2]; getLocationOnScreen(loc);
        float[] b=visibleRowBounds();
        int rowTop=(int)(b[4]*getHeight());
        popup.showAtLocation(this,Gravity.TOP|Gravity.LEFT,
                Math.max(4,Math.min(loc[0]+dp(4),getResources().getDisplayMetrics().widthPixels-dp(400))),
                Math.max(4,loc[1]+rowTop-dp(72)));
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        float x=e.getX(), y=e.getY();
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            stopRepeat();
            if(touchResizeRoller(x,y)){ clearPressGlowNow(); return true; }
            pressRectFor(x,y,true);
            if(isCapsAt(x,y)){
                capsHoldTriggered=false;
                handler.removeCallbacks(capsHoldAction);
                handler.postDelayed(capsHoldAction,450);
                return true;
            }
            if(isAlifAt(x,y)){
                alifLongPressTriggered=false;
                handler.removeCallbacks(alifLongPressAction);
                handler.postDelayed(alifLongPressAction,450);
                return true;
            }
            handle(x,y);
            if(isRepeatableSymbolAt(x,y)) startSymbolRepeat(x,y);
            if(!isBackspaceAt(x,y)){
                pressHeld=false;
                handler.removeCallbacks(clearPressGlow);
                handler.postDelayed(clearPressGlow,140);
            }
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE){
            if(touchResizeRoller(x,y)) return true;
            if(isCapsAt(x,y) && capsHoldTriggered){ invalidate(); }
            return true;
        }
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){
            stopRepeat();
            handler.removeCallbacks(capsHoldAction);
            handler.removeCallbacks(alifLongPressAction);
            if(isAlifAt(x,y) && action==MotionEvent.ACTION_UP){
                if(!alifLongPressTriggered){
                    service.type("ا");
                }
                alifLongPressTriggered=false;
                clearPressGlowNow();
                return true;
            }
            if(isCapsAt(x,y) && action==MotionEvent.ACTION_UP){
                if(capsHoldTriggered){
                    capsHold=false;
                    capsMode=0;
                } else {
                    long now=System.currentTimeMillis();
                    if(now-lastCapsTap<=350){ capsMode=2; }
                    else { capsMode=(capsMode==1?0:1); }
                    lastCapsTap=now;
                }
                invalidate();
            }
            clearPressGlowNow();
        }
        return true;
    }

    private boolean isCapsAt(float x,float y){
        int row=getRowAt(y);
        if(row!=5) return false;
        float g=dp(4),left=g,right=getWidth()-g,total=right-left,eqW=total*.105f;
        return x>=left && x<=left+eqW;
    }

    private boolean isRepeatableSymbolAt(float x,float y){
        int row=getRowAt(y);
        if(row!=6) return false;
        float[] weights={0.065f,0.095f,0.090f,0.205f,0.060f,0.060f,0.070f,0.110f,0.110f,0.060f,0.060f};
        float g=dp(4),total=0f;for(float q:weights)total+=q;float scale=(getWidth()-g*(weights.length+1))/total,pos=g;
        for(int i=0;i<weights.length;i++){float cw=weights[i]*scale;if(x>=pos&&x<=pos+cw)return i>=7;pos+=cw+g;}
        return false;
    }

    private void startSymbolRepeat(float x,float y){
        stopRepeat();
        repeatX=x; repeatY=y;
        repeat=()->{ handle(repeatX,repeatY); handler.postDelayed(repeat,110); };
        handler.postDelayed(repeat,600);
    }

    private boolean isBackspaceAt(float x,float y){
        if(getRowAt(y)!=2) return false;
        float g=dp(4),left=g,right=getWidth()-g,backW=(right-left)*0.145f;
        return x>=right-backW;
    }

    // Hit-test uses exactly the same geometry as the drawn keyboard cells.
    private int keyIndexAtExactDrawnCell(float x, float left, float right, int count){
        float g=dp(4);
        float cw=(right-left-g*(count-1))/count;
        for(int i=0;i<count;i++){
            float l=left+i*(cw+g), r=l+cw;
            if(x>=l && x<=r) return i;
        }
        return -1;
    }

    private int indexForEqualRow(float x, float left, float right, int count){
        float g=dp(4);
        float cw=(right-left-g*(count-1))/count;
        for(int i=0;i<count;i++){ float l=left+i*(cw+g); if(x>=l && x<=l+cw) return i; }
        return -1;
    }
    private int topToolbarIndex(float x){
        // Must mirror drawTopToolbar() exactly: nine weighted buttons followed by
        // a separately sized Resize button. This prevents taps from activating neighbors.
        float[] weights={0.100f,0.100f,0.090f,0.080f,0.080f,0.080f,0.090f,0.100f,0.100f};
        float g=dp(4);
        float alphaEnter=(getWidth()-g*2)*.115f;
        float alphaW=(getWidth()-g*2-alphaEnter-g*10)/11f;
        float total=0f;for(float q:weights)total+=q;
        float scale=(getWidth()-g*(weights.length+2)-alphaW)/total;
        float pos=g;
        for(int i=0;i<weights.length;i++){
            float cw=weights[i]*scale;
            if(x>=pos && x<=pos+cw) return i;
            pos+=cw+g;
        }
        if(x>=pos && x<=pos+alphaW) return 9;
        return -1;
    }
    private int suggestionIndex(float x){
        float g=dp(5);
        float[] widths={0.125f,0.125f,0.125f,0.125f,0.125f,0.125f,0.10f};
        float usable=getWidth()-g*(widths.length-1), total=0f;
        for(float q:widths) total+=q;
        float scale=usable/total, pos=g;
        for(int i=0;i<7;i++){
            float cw=widths[i]*scale;
            if(x>=pos&&x<=pos+cw) return i;
            pos+=cw+g;
        }
        return -1;
    }

    private void handle(float x,float y){
        int row=getRowAt(y); if(row<0||row>6)return;
        float g=dp(4);
        if(row==0){
            int i=topToolbarIndex(x); if(i<0)return;
            if(i==0)service.copyAll();
            else if(i==1)service.copyScreen();
            else if(i==2)service.paste();
            else if(i==3)service.cut();
            else if(i==4){service.undo();startUndoRepeat();}
            else if(i==5){service.redo();startRedoRepeat();}
            else if(i==6)showClipboardHistory();
            else if(i==7)showDrawer();
            else if(i==8)showMouseControls();
            else if(i==9){
                resizeRollerOpen=!resizeRollerOpen;
                setBackgroundColor(Color.TRANSPARENT);
                service.setKeyboardResizeMode(resizeRollerOpen, keyboardScale);
                invalidate();
            }
            return;
        }
        if(row==1){
            float left=g,right=getWidth()-g,cw=(right-left-g*6f)/7f;
            int i=(int)((x-left)/(cw+g));
            if(i<0 || i>=7) return;
            float cellLeft=left+i*(cw+g);
            if(x>cellLeft+cw) return;
            if(i<suggestions.length){
                String suggestion=suggestions[i];
                if(suggestion!=null && !suggestion.isEmpty()) service.replaceCurrentWord(suggestion);
            }
            return;
        }
        if(row==2){
            float left=g,right=getWidth()-g,backW=(right-left)*0.145f,normalArea=right-left-backW-g;
            float cw=(normalArea-g*9)/10f;
            if(x>=right-backW){startBackspace();return;}
            int i=-1;for(int k=0;k<10;k++){float l=left+k*(cw+g);if(x>=l&&x<=l+cw){i=k;break;}}
            if(i<0)return;
            String[] keys={"1","2","3","4","5","6","7","8","9","0"};
            String[] symbols={"!","@","#","$","%","^","&","*","(",")"};
            service.type((capsMode!=0 || capsHold)?symbols[i]:keys[i]);
            if(capsMode==1 && !capsHold) capsMode=0;
            return;
        }
        if(row==3 || row==4){
            float left=g,right=getWidth()-g,enterW=(right-left)*0.115f,normalRight=right-enterW-g;
            if(x>=normalRight+g){service.enter();return;}
            String[] keys;
            if(englishMode) keys=row==3?new String[]{"q","w","e","r","t","y","u","i","o","p","["}:new String[]{"a","s","d","f","g","h","j","k","l",";","'"};
            else keys=row==3?new String[]{"ض","ص","ث","ق","ف","غ","ع","ه","خ","ح","ج"}:new String[]{"ش","س","ی","ب","ل","ا","ت","ن","م","ک","گ"};
            int i=keyIndexAtExactDrawnCell(x,left,normalRight,keys.length);if(i<0)return;
            boolean upper=(capsMode!=0 || capsHold);
            service.type(englishMode?(upper?keys[i].toUpperCase(Locale.US):keys[i]):keys[i]);
            if(capsMode==1 && !capsHold) capsMode=0;
            return;
        }
        if(row==5){
            float left=g,right=getWidth()-g,total=right-left,eqW=total*0.105f;
            float oldCapsW=(total-eqW-g*11f)/11f;
            float pos=left; int i=-1;
            for(int k=0;k<11;k++){ float w=(k==0)?eqW:oldCapsW; if(x>=pos&&x<=pos+w){i=k;break;} pos+=w+g; }
            if(i<0 && x>=pos && x<=right){
                service.type(equalUnderscoreNext ? "_" : "=");
                equalUnderscoreNext=!equalUnderscoreNext;
                return;
            }
            if(i<0)return;
            String[] keys=englishMode?new String[]{"Caps","z","x","c","v","b","n","m",",",".","/"}:new String[]{"Caps","ظ","ط","ژ","ز","ر","ذ","د","پ","و","چ"};
            if(i==0){return;}
            boolean upper=(capsMode!=0 || capsHold);
            service.type(englishMode?(upper?keys[i].toUpperCase(Locale.US):keys[i]):keys[i]);
            if(capsMode==1 && !capsHold) capsMode=0;
            return;
        }
        if(row==6){
            float[] weights={0.065f,0.095f,0.090f,0.205f,0.060f,0.060f,0.070f,0.110f,0.110f,0.060f,0.060f};
            float total=0f;for(float q:weights)total+=q;float scale=(getWidth()-g*(weights.length+1))/total,pos=g;int bi=-1;
            for(int i=0;i<weights.length;i++){float cw=weights[i]*scale;if(x>=pos&&x<=pos+cw){bi=i;break;}pos+=cw+g;}
            if(bi==0){showEmojiPicker();return;}
            if(bi==1){showSymbolPicker();return;}
            if(bi==2){englishMode=!englishMode;invalidate();return;}
            if(bi==3){service.type(" ");return;}
            if(bi==4){service.type("،");return;}
            if(bi==5){service.type("؟");return;}
            if(bi==6){service.type("+");return;}
            if(bi==7){service.move(KeyEvent.KEYCODE_DPAD_LEFT);return;}
            if(bi==8){service.move(KeyEvent.KEYCODE_DPAD_RIGHT);return;}
            if(bi==9){service.move(KeyEvent.KEYCODE_DPAD_UP);return;}
            if(bi==10){service.move(KeyEvent.KEYCODE_DPAD_DOWN);return;}
        }
    }

    private void showMouseControls(){
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,8,10,8);
        final Button[] headerClose = new Button[1];
        LinearLayout mouseHeader = new LinearLayout(service);
        mouseHeader.setOrientation(LinearLayout.HORIZONTAL);
        mouseHeader.setGravity(Gravity.CENTER_VERTICAL);
        mouseHeader.setPadding(dp(6), dp(4), dp(6), dp(4));
        mouseHeader.setBackgroundColor(Color.rgb(245,245,245));

        Button leftHeader = drawerButton("کلیک چپ");
        leftHeader.setAllCaps(false);
        leftHeader.setTextSize(12);
        leftHeader.setMinWidth(0);
        leftHeader.setMinimumWidth(0);
        leftHeader.setPadding(dp(8),0,dp(8),0);
        leftHeader.setOnClickListener(v -> FastKeyboardAccessibilityService.click(false));

        Button rightHeader = drawerButton("کلیک راست");
        rightHeader.setAllCaps(false);
        rightHeader.setTextSize(12);
        rightHeader.setMinWidth(0);
        rightHeader.setMinimumWidth(0);
        rightHeader.setPadding(dp(8),0,dp(8),0);
        rightHeader.setOnClickListener(v -> FastKeyboardAccessibilityService.click(true));

        TextView mouseTitle = new TextView(service);
        mouseTitle.setText("موس صفحه وب");
        mouseTitle.setTextSize(18);
        mouseTitle.setTextColor(BLACK);
        mouseTitle.setGravity(Gravity.CENTER);

        Button mouseClose = drawerButton("×");
        mouseClose.setTextSize(18);
        mouseClose.setMinWidth(0);
        mouseClose.setMinimumWidth(0);
        mouseClose.setPadding(0,0,0,0);
        headerClose[0] = mouseClose;

        mouseHeader.addView(leftHeader, new LinearLayout.LayoutParams(0, dp(42), 1f));
        mouseHeader.addView(rightHeader, new LinearLayout.LayoutParams(0, dp(42), 1f));
        mouseHeader.addView(mouseTitle, new LinearLayout.LayoutParams(0, dp(42), 2f));
        mouseHeader.addView(mouseClose, new LinearLayout.LayoutParams(dp(46), dp(42)));
        root.addView(mouseHeader, new LinearLayout.LayoutParams(-1, dp(50)));

        TextView info=new TextView(service);
        info.setText(FastKeyboardAccessibilityService.isEnabled() ? "پد را بکشید تا نشانگر موس حرکت کند. برای کلیک، دکمه چپ یا راست را بزنید." : "برای کار واقعی موس روی صفحات وب، ابتدا دسترسی «F Keys 11 Mouse» را فعال کنید.");
        info.setTextSize(14); info.setGravity(Gravity.CENTER); root.addView(info,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout actions=new LinearLayout(service); actions.setGravity(Gravity.CENTER);
        Button enable=new Button(service); enable.setText("فعال‌سازی موس"); enable.setAllCaps(false);
        enable.setOnClickListener(v -> { try { service.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch(Exception ignored){} });
        actions.addView(enable,new LinearLayout.LayoutParams(0,dp(50),1f));
        root.addView(actions);
        final MousePadView pad=new MousePadView(service);
        root.addView(pad,new LinearLayout.LayoutParams(-1,0,1f));
        // Left/right click buttons are intentionally on the window title bar.
        Button stop=drawerButton("خاموش کردن موس");
        stop.setOnClickListener(v -> FastKeyboardAccessibilityService.disable());
        root.addView(stop);
        final PopupWindow popup=new PopupWindow(root,Math.max(dp(280),getWidth()-dp(16)),Math.min(dp(600),Math.max(dp(420),getHeight()-dp(12))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private class MousePadView extends View {
        private final Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastX,lastY;
        MousePadView(Context c){super(c);setBackgroundColor(Color.rgb(245,247,250));}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c); float w=getWidth(),h=getHeight();
            mp.setStyle(Paint.Style.STROKE); mp.setStrokeWidth(dp(3)); mp.setColor(NAVY); c.drawRoundRect(dp(8),dp(8),w-dp(8),h-dp(8),dp(18),dp(18),mp);
            mp.setStyle(Paint.Style.FILL); mp.setColor(Color.LTGRAY); c.drawCircle(w/2f,h/2f,dp(20),mp);
            mp.setColor(NAVY); mp.setTextSize(dp(16)); mp.setTextAlign(Paint.Align.CENTER); c.drawText("حرکت نشانگر",w/2f,h/2f+dp(55),mp);
        }
    public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getX()-lastX,dy=e.getY()-lastY;lastX=e.getX();lastY=e.getY();FastKeyboardAccessibilityService.movePointer(dx*1.8f,dy*1.8f);return true;}
            return true;
        }
    }

    private void showSymbolPicker(){
        final String[] symbols={"!","@","#","$","%","^","&","*","(",")","-","_","=","+","[","]","{","}","\\","|",";",":",",","<",".",">","/","؟","«","»","،","؛","٪","×","÷","±","≈","≠","≤","≥","∞","√","∑","π","µ","°","′","″","§","©","®","™","€","£","¥","₽","₹","$","¢","…","—","–","·","•","‰","※","†","‡","✓","✔","✕","✖","★","☆","♪","♫","♩","♥","♦","♣","♠","♂","♀","←","→","↑","↓","↔","↕","⇐","⇒","⇑","⇓","↗","↘","↙","↖","⌘","⌫","⌁","⌛","⚠","☑","☒","☀","☁","☂","☃","☄","☾","☽","♨","⚡","☕","☎","✉","✈","⚓","⚙","⚽","⚾","♟","♞","♜","♛","♚"};
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,10,10,10);
        final Button[] headerClose = new Button[1];
        addPopupHeader(root, "نمادها", headerClose);
        ScrollView scroll=new ScrollView(service);
        GridLayout grid=new GridLayout(service); grid.setColumnCount(6); grid.setPadding(4,4,4,4);
        for(String s:symbols){
            Button b=new Button(service); b.setText(s); b.setTextSize(20); b.setAllCaps(false); b.setTextColor(Color.RED);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(58); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(2,2,2,2); grid.addView(b,lp);
            b.setOnClickListener(v -> service.typeUnit(s));
            setSymbolButtonRepeat(b,s);
        }
        String[] extraSymbols={"⌁","⌂","⌃","⌄","⌘","⌥","⌃","⇧","⇪","↩","↪","⤴","⤵","↶","↷","⟳","⟲","⟶","⟵","⟷","⤒","⤓","⇤","⇥","⇠","⇢","⇡","⇣","↖","↗","↘","↙","↺","↻","⏎","␣","⌫","⌦","⎋","⏎","⏪","⏩","⏮","⏭","⏯","⏸","⏹","⏺","⏱","⏲","⏰","♩","♪","♫","♬","♭","♯","𝄞","∞","∝","∂","∇","∫","∬","∭","∮","∴","∵","∀","∃","∄","∅","∈","∉","⊂","⊃","⊆","⊇","∪","∩","∧","∨","¬","⊕","⊗","⊙","⊥","∥","∠","∟","△","▲","▼","◆","◇","■","□","●","○","◉","◎","◌","◍","◐","◑","◒","◓","☑","☒","☐","✓","✔","✗","✘","✦","✧","✩","✪","✫","✬","✭","✮","✯","✰","☮","☯","☪","✡","☸","♈","♉","♊","♋","♌","♍","♎","♏","♐","♑","♒","♓","♀","♂","⚕","⚖","⚗","⚔","⚑","⚐","⚜","♻","☢","☣","⚠","⛔","🚫","🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤","🔶","🔷","🔺","🔻","◀","▶","⏫","⏬","⬅","➡","⬆","⬇","↔","↕","↯","⇐","⇒","⇑","⇓"};
        for(String s:extraSymbols){ Button b=new Button(service); b.setText(s); b.setTextSize(20); b.setAllCaps(false); b.setTextColor(Color.RED); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(58); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(2,2,2,2); grid.addView(b,lp); b.setOnClickListener(v->service.typeUnit(s)); setSymbolButtonRepeat(b,s); }
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2)); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        String[] moreSymbols={"⌖","⌗","⌑","⌘","⌥","⇥","⇤","↹","␍","␊","␉","␠","⌦","⌫","⎋","⏎","⌧","⌨","⏏","⏮","⏪","⏩","⏭","⏯","⏸","⏹","⏺","⏱","⏲","⏰","⏳","∎","□","■","▢","▣","▤","▥","▦","▧","▨","▩","▪","▫","▬","▭","▲","△","▼","▽","◆","◇","◈","◉","○","●","◌","◍","◐","◑","◒","◓","◔","◕","◖","◗","◠","◡","◢","◣","◤","◥","※","⁂","⁑","⁕","⁖","⁘","⁙","⁜","⁝","⁞","‖","¦","‗","¯","ˉ","ˊ","ˋ","˙","¨","ˆ","˜","˚","¸","˛","˝","ˇ","¡","¿","‹","›","„","“","”","‘","’","‚","«","»","⟨","⟩","⟪","⟫","⟦","⟧","⟮","⟯","⦃","⦄","∈","∉","∋","∌","⊂","⊃","⊄","⊅","⊆","⊇","⊈","⊉","∪","∩","⊎","⊓","⊔","∧","∨","⊻","¬","⊢","⊣","⊨","⊭","⊤","⊥","∥","∦","∝","∼","≃","≅","≡","≢","≈","≉","≠","≮","≯","≤","≥","≪","≫","∓","∔","∕","∗","∘","∙","∶","∷","∴","∵","∽","∾","∿","∫","∬","∭","∮","∯","∰","∇","∆","∂","ℏ","ℓ","℘","ℜ","ℑ","ℵ","ℕ","ℤ","ℚ","ℝ","ℂ","°","′","″","‴","‰","‱","№","℗","℠","™","©","®","℮","₿","₽","₺","₴","₩","₦","₫","₡","₲","₵","₸","₹","€","£","¥","¢","¤","₱","₪","﷼","٪","٫","٬","ـ","‍","‌","﻿","​","…","⋯","⋮","⋰","⋱","—","–","‑","‒","―","_","-","+","=","*","/","\\","|","~","`","^","%","&","@","#","$"};
for(String s:moreSymbols){ Button b=new Button(service); b.setText(s); b.setTextSize(20); b.setAllCaps(false); b.setTextColor(Color.RED); GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(56); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(2,2,2,2); grid.addView(b,lp); b.setOnClickListener(v->service.typeUnit(s)); setSymbolButtonRepeat(b,s); }
        final PopupWindow popup=new PopupWindow(root,Math.min(dp(360),Math.max(dp(300),getWidth()-dp(16))),Math.min(dp(620),Math.max(dp(360),getHeight()-dp(16))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        headerClose[0].setOnClickListener(v->popup.dismiss());
        popup.showAtLocation(this,Gravity.CENTER,0,0);

    }

    private void setSymbolButtonRepeat(Button b, String symbol){
        b.setOnTouchListener((v,e)->{
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                service.typeUnit(symbol);
                stopRepeat();
                repeat=()->{ service.typeUnit(symbol); handler.postDelayed(repeat,110); };
                handler.postDelayed(repeat,600);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){
                stopRepeat();
                return true;
            }
            return true;
        });
    }

    private void startBackspace(){
        service.backspace();
        stopRepeat();
        repeat=()->{ service.backspace(); handler.postDelayed(repeat,55); };
        handler.postDelayed(repeat,600);
    }

    private void startUndoRepeat(){
        stopRepeat();
        repeat=()->{ service.undo(); handler.postDelayed(repeat,80); };
        handler.postDelayed(repeat,350);
    }

    private void startRedoRepeat(){
        stopRepeat();
        repeat=()->{ service.redo(); handler.postDelayed(repeat,80); };
        handler.postDelayed(repeat,350);
    }

    private void stopRepeat(){
        if(repeat!=null){
            handler.removeCallbacks(repeat);
            repeat=null;
        }
    }
    private void showSteeringWheel() {
        // Steering Wheel UI placeholder; keeps the requested action available.
    }

    private void showArabicHarakat() {
        // Arabic Harakat UI placeholder; keeps the requested action available.
    }

    private void addEmojiButton(android.widget.GridLayout grid, String emoji) {
        android.widget.Button btn = new android.widget.Button(getContext());
        btn.setText(emoji);
        btn.setOnClickListener(v -> service.typeUnit(emoji));
        android.widget.GridLayout.LayoutParams lp = new android.widget.GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = dp(56);
        lp.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f);
        lp.setMargins(2, 2, 2, 2);
        grid.addView(btn, lp);
    }

    private String flagFromCode(String code) {
        if (code == null || code.length() != 2) return code == null ? "" : code;
        String upper = code.toUpperCase(java.util.Locale.US);
        int first = upper.charAt(0);
        int second = upper.charAt(1);
        if (first < 'A' || first > 'Z' || second < 'A' || second > 'Z') return code;
        return new String(Character.toChars(0x1F1E6 + first - 'A'))
                + new String(Character.toChars(0x1F1E6 + second - 'A'));
    }

}
