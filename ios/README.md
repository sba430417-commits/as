# Your Account for iOS

هذا المجلد يحتوي نسخة مصدر أولية مكتوبة بـ SwiftUI، وتشمل شاشة الدخول بخياري Face ID / Touch ID أو رمز الجهاز، وقائمة الحسابات وشاشة إضافة حساب.

إخراج ملف `.ipa` يتطلب جهاز macOS مع Xcode وحساب Apple Developer للتوقيع. لا يمكن بناء IPA موقّع من GitHub Actions المجاني أو بيئة Linux الحالية بدون إعدادات شهادات Apple وملف provisioning profile.

قبل النشر الفعلي يجب ربط التخزين بـ Keychain أو Secure Enclave، وعدم حفظ كلمات المرور في UserDefaults أو كنص صريح.
