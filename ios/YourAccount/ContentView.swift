import SwiftUI

struct VaultAccount: Identifiable {
    let id = UUID()
    var site: String
    var username: String
    var password: String
}

struct ContentView: View {
    @State private var isUnlocked = false
    @State private var accounts: [VaultAccount] = []
    @State private var showingAdd = false
    @State private var showingLoginOptions = false

    var body: some View {
        NavigationStack {
            Group {
                if isUnlocked {
                    List(accounts) { account in
                        VStack(alignment: .leading, spacing: 5) {
                            Text(account.site).font(.headline).foregroundStyle(.primary)
                            Text(account.username).font(.subheadline).foregroundStyle(.secondary)
                        }
                    }
                    .listStyle(.insetGrouped)
                } else {
                    VStack(spacing: 18) {
                        Image(systemName: "lock.shield.fill").font(.system(size: 54)).foregroundStyle(.blue)
                        Text("Your Account").font(.largeTitle.bold())
                        Text("بياناتك محفوظة بشكل آمن على جهازك").foregroundStyle(.secondary)
                        Button("تسجيل الدخول") { showingLoginOptions = true }
                            .buttonStyle(.borderedProminent)
                            .controlSize(.large)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(24)
                }
            }
            .navigationTitle(isUnlocked ? "حساباتك" : "")
            .toolbar {
                if isUnlocked {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button { showingAdd = true } label: { Image(systemName: "plus") }
                    }
                }
            }
            .sheet(isPresented: $showingAdd) {
                AddAccountView { account in accounts.append(account) }
            }
            .confirmationDialog("طريقة تسجيل الدخول", isPresented: $showingLoginOptions) {
                Button("Face ID / Touch ID") { authenticateWithBiometrics() }
                Button("رمز PIN للجوال") { authenticateWithDevicePasscode() }
                Button("إلغاء", role: .cancel) {}
            }
        }
    }

    private func authenticateWithBiometrics() { isUnlocked = true }
    private func authenticateWithDevicePasscode() { isUnlocked = true }
}

struct AddAccountView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var site = ""
    @State private var username = ""
    @State private var password = ""
    let onSave: (VaultAccount) -> Void

    var body: some View {
        NavigationStack {
            Form {
                Section("بيانات الحساب") {
                    TextField("اسم الموقع أو التطبيق", text: $site)
                    TextField("اسم المستخدم", text: $username)
                    SecureField("كلمة المرور", text: $password)
                }
                Section {
                    Button("حفظ الحساب") {
                        guard !site.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                        onSave(VaultAccount(site: site, username: username, password: password))
                        dismiss()
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .navigationTitle("إضافة حساب جديد")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("إلغاء") { dismiss() } } }
        }
    }
}
