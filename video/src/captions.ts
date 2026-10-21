/**
 * Every caption in the video, English with its Arabic line. Frames are relative to the scene's start
 * (30 frames = 1 second). Edit the text here; the scenes read it from this file.
 */
export type Caption = {from: number; to: number; en: string; ar: string};

export const CAPTIONS: Record<string, Caption[]> = {
  oldWay: [
    {from: 30, to: 215, en: 'Ads. Sponsored rows. Apps you never chose.', ar: 'إعلانات، وصفوف مموّلة، وتطبيقات لم تخترها.'},
    {from: 230, to: 440, en: "Your TV's home screen isn't yours.", ar: 'الشاشة الرئيسية لتلفازك ليست لك.'},
  ],
  meet: [
    {from: 55, to: 140, en: 'Meet Shamash.', ar: 'تعرّف على شمش.'},
    {from: 150, to: 292, en: 'A home screen shaped around you.', ar: 'شاشة رئيسية تتشكّل حولك.'},
  ],
  setup: [
    {from: 12, to: 165, en: 'Setup that asks about you.', ar: 'إعداد يبدأ بالسؤال عنك.'},
    {from: 172, to: 322, en: 'Your name, city, apps, teams and tastes.', ar: 'اسمك، ومدينتك، وتطبيقاتك، وفرقك، وذوقك.'},
  ],
  home: [
    {from: 10, to: 116, en: 'Your services. Your rows.', ar: 'خدماتك أنت، وصفوفك أنت.'},
    {from: 126, to: 236, en: 'A profile for everyone, and a lock for Kids.', ar: 'ملف لكل فرد في العائلة، وقفل لملف الأطفال.'},
    {from: 246, to: 356, en: 'Reorder, hide, pin. Your home, your way.', ar: 'رتّب، وأخفِ، وثبّت. شاشتك كما تريد.'},
    {from: 366, to: 472, en: 'One press: Cinema, Sports, Kids or Minimal.', ar: 'بضغطة واحدة: سينما، أو رياضة، أو أطفال، أو بسيط.'},
  ],
  onePlace: [
    {from: 10, to: 110, en: 'One search across all your services.', ar: 'بحث واحد في كل خدماتك.'},
    {from: 120, to: 225, en: "Live scores over whatever you're watching.", ar: 'النتائج المباشرة فوق أي شيء تشاهده.'},
    {from: 235, to: 335, en: 'Your own servers, channels and feeds.', ar: 'خوادمك الخاصة، وقنواتك، وخلاصاتك.'},
    {from: 345, to: 442, en: 'Your phone is the remote, and the queue.', ar: 'هاتفك هو جهاز التحكم، وقائمة الانتظار.'},
  ],
  why: [
    {from: 15, to: 185, en: 'Why switch?', ar: 'لماذا تنتقل إلى شمش؟'},
    {from: 195, to: 322, en: 'Nothing uninstalled. One press brings the original back.', ar: 'لا شيء يُحذف، وضغطة واحدة تعيد الشاشة الأصلية.'},
  ],
};

export const OUTRO = {
  en: 'Fork it. Make it yours.',
  ar: 'انسخه، واجعله لك.',
  url: 'github.com/mamer12/shamash',
};
