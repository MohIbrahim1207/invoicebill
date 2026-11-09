package com.billing.invoicehub.service;

import com.billing.invoicehub.entity.AppUser;
import com.billing.invoicehub.entity.LegalAcceptance;
import com.billing.invoicehub.entity.LegalDocument;
import com.billing.invoicehub.entity.LegalHistory;
import com.billing.invoicehub.repository.AppUserRepository;
import com.billing.invoicehub.repository.LegalAcceptanceRepository;
import com.billing.invoicehub.repository.LegalDocumentRepository;
import com.billing.invoicehub.repository.LegalHistoryRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LegalDocumentService {

    private static final Logger log = LoggerFactory.getLogger(LegalDocumentService.class);

    public static final String SLUG_TERMS = "terms";
    public static final String SLUG_PRIVACY = "privacy";
    public static final String SLUG_COOKIES = "cookies";

    private final LegalDocumentRepository documentRepository;
    private final LegalHistoryRepository historyRepository;
    private final LegalAcceptanceRepository acceptanceRepository;
    private final AppUserRepository userRepository;
    private final AuditLogService auditLogService;

    public LegalDocumentService(LegalDocumentRepository documentRepository,
                                LegalHistoryRepository historyRepository,
                                LegalAcceptanceRepository acceptanceRepository,
                                AppUserRepository userRepository,
                                @Autowired(required = false) AuditLogService auditLogService) {
        this.documentRepository = documentRepository;
        this.historyRepository = historyRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @PostConstruct
    public void init() {
        seedDefaultDocumentsIfEmpty();
    }

    public synchronized void seedDefaultDocumentsIfEmpty() {
        try {
            if (documentRepository.findBySlug(SLUG_TERMS).isEmpty()) {
                createInitialDocument(SLUG_TERMS, "1.0",
                        "Terms of Use",
                        "Syarat dan Ketentuan Penggunaan",
                        getDefaultTermsEn(),
                        getDefaultTermsId());
            }

            if (documentRepository.findBySlug(SLUG_PRIVACY).isEmpty()) {
                createInitialDocument(SLUG_PRIVACY, "1.0",
                        "Privacy Policy",
                        "Kebijakan Privasi",
                        getDefaultPrivacyEn(),
                        getDefaultPrivacyId());
            }

            if (documentRepository.findBySlug(SLUG_COOKIES).isEmpty()) {
                createInitialDocument(SLUG_COOKIES, "1.0",
                        "Cookie Policy",
                        "Kebijakan Cookie",
                        getDefaultCookiesEn(),
                        getDefaultCookiesId());
            }
        } catch (Exception ex) {
            log.warn("Could not seed default legal documents: {}", ex.getMessage());
        }
    }

    private void createInitialDocument(String slug, String version, String titleEn, String titleId, String contentEn, String contentId) {
        LegalDocument doc = new LegalDocument(slug, version, titleEn, titleId, contentEn, contentId, "system");
        documentRepository.save(doc);
        historyRepository.save(new LegalHistory(slug, version, titleEn, titleId, contentEn, contentId, "system"));
        log.info("Seeded default legal document: {} (v{})", slug, version);
    }

    public Optional<LegalDocument> getDocument(String slug) {
        return documentRepository.findBySlug(slug);
    }

    public List<LegalHistory> getHistory(String slug) {
        return historyRepository.findAllBySlugOrderByCreatedAtDesc(slug);
    }

    public String getCurrentVersion(String slug) {
        return getDocument(slug).map(LegalDocument::getVersion).orElse("1.0");
    }

    @Transactional
    public LegalDocument updateDocument(String slug, String version, String titleEn, String titleId,
                                        String contentEn, String contentId, String updatedBy) {
        LegalDocument doc = documentRepository.findBySlug(slug)
                .orElseGet(() -> new LegalDocument(slug, version, titleEn, titleId, contentEn, contentId, updatedBy));

        doc.setVersion(version != null && !version.isBlank() ? version.trim() : "1.0");
        doc.setTitleEn(titleEn);
        doc.setTitleId(titleId);
        doc.setContentEn(contentEn);
        doc.setContentId(contentId);
        doc.setUpdatedAt(LocalDateTime.now());
        doc.setUpdatedBy(updatedBy != null ? updatedBy : "super_admin");

        LegalDocument saved = documentRepository.save(doc);

        // Record history snapshot
        LegalHistory history = new LegalHistory(slug, doc.getVersion(), titleEn, titleId, contentEn, contentId, doc.getUpdatedBy());
        historyRepository.save(history);

        if (auditLogService != null) {
            auditLogService.log(
                    doc.getUpdatedBy(),
                    "ROLE_SUPER_ADMIN",
                    "UPDATE_LEGAL_DOCUMENT",
                    "LEGAL_DOCUMENT",
                    saved.getId(),
                    null,
                    String.format("Updated legal document '%s' to version %s", slug, doc.getVersion())
            );
        }

        return saved;
    }

    public boolean hasUserAcceptedCurrentTerms(AppUser user) {
        if (user == null) return true;
        // Staff users are exempt from the vendor acceptance blocker, but we still track it
        String activeTermsVersion = getCurrentVersion(SLUG_TERMS);
        String userVersion = user.getTermsAcceptedVersion();
        return userVersion != null && userVersion.equalsIgnoreCase(activeTermsVersion);
    }

    @Transactional
    public void recordAcceptance(AppUser user, String ipAddress) {
        if (user == null) return;
        String activeTerms = getCurrentVersion(SLUG_TERMS);
        String activePrivacy = getCurrentVersion(SLUG_PRIVACY);

        user.setTermsAcceptedVersion(activeTerms);
        user.setTermsAcceptedAt(LocalDateTime.now());
        userRepository.save(user);

        LegalAcceptance acceptance = new LegalAcceptance(user.getUsername(), activeTerms, activePrivacy, ipAddress);
        acceptanceRepository.save(acceptance);

        if (auditLogService != null) {
            auditLogService.log(
                    user.getUsername(),
                    user.getUserDomain() != null ? user.getUserDomain() : "VENDOR",
                    "ACCEPT_LEGAL_TERMS",
                    "LEGAL_ACCEPTANCE",
                    acceptance.getId(),
                    null,
                    String.format("User accepted Terms (v%s) & Privacy (v%s)", activeTerms, activePrivacy)
            );
        }
    }

    // ─── Default Content Builders (Marked DRAFT – to be reviewed) ────────────

    private String getDefaultTermsEn() {
        return """
                ### DRAFT – TO BE REVIEWED

                **Effective Date:** 09 November 2026 | **Version:** 1.0

                Welcome to **InvoiceHub**, an enterprise vendor invoice management and quotation billing platform operated by **PT Flow Force Indonesia** ("Company", "we", "us", or "our").

                #### 1. Acceptance of Terms
                By accessing, registering for, or using the InvoiceHub portal, you ("Vendor", "User", or "you") agree to be bound by these Terms of Use and our Privacy Policy. If you do not agree to these terms, do not access or use InvoiceHub.

                #### 2. Account Registration and Security
                - You must provide accurate, current, and complete corporate and contact information during registration, including your company name, tax identification (NPWP / GST), and valid representative credentials.
                - You are responsible for safeguarding your credentials (including two-factor authentication tokens) and are fully liable for all activities under your account.
                - You must promptly notify us at `automation@flow-force.com` of any unauthorized access or security breach.

                #### 3. Invoice Submissions and Truthfulness
                - Vendors must only submit authentic, verified invoices, purchase orders, delivery receipts (Surat Jalan), and tax documents (Faktur Pajak) that correspond to actual transactions.
                - Submission of fraudulent, duplicate, forged, or altered invoices constitutes a material breach and may result in immediate account termination and legal action.

                #### 4. System Usage and Acceptable Conduct
                - You may not reverse-engineer, decompile, probe, or scan our platform for security vulnerabilities.
                - You may not attempt prompt injection, automated scraping, or unauthorized access to other vendors' documents or data.
                - Internal quotation conversion and AI creation tools are restricted to authorized internal staff only.

                #### 5. Termination and Modifications
                We reserve the right to suspend or terminate accounts that violate these Terms or Indonesian commercial laws. We may amend these Terms periodically. Continued use following notification constitutes acceptance of updated terms.

                #### 6. Governing Law and Dispute Resolution
                These Terms are governed by and construed in accordance with the laws of the **Republic of Indonesia**. Any disputes arising under these Terms shall be resolved through amicable negotiation or via the District Court of South Jakarta.

                **Contact:** For inquiries regarding these Terms, contact `automation@flow-force.com`.
                """;
    }

    private String getDefaultTermsId() {
        return """
                ### DRAF – UNTUK DITINJAU

                **Tanggal Berlaku:** 09 November 2026 | **Versi:** 1.0

                Selamat datang di **InvoiceHub**, platform manajemen faktur vendor dan penagihan enterprise yang dioperasikan oleh **PT Flow Force Indonesia** ("Perusahaan", "kami").

                #### 1. Penerimaan Syarat
                Dengan mengakses, mendaftar, atau menggunakan portal InvoiceHub, Anda ("Vendor", "Pengguna", atau "Anda") setuju untuk terikat oleh Syarat dan Ketentuan ini serta Kebijakan Privasi kami. Jika Anda tidak menyetujui ketentuan ini, jangan mengakses atau menggunakan InvoiceHub.

                #### 2. Pendaftaran Akun dan Keamanan
                - Anda wajib memberikan informasi perusahaan dan kontak yang akurat, terkini, dan lengkap saat pendaftaran, termasuk nama perusahaan, Nomor Pokok Wajib Pajak (NPWP), dan kredensial perwakilan yang sah.
                - Anda bertanggung jawab menjaga kerahasiaan kredensial akun Anda (termasuk autentikasi dua faktor) dan bertanggung jawab penuh atas segala aktivitas pada akun Anda.
                - Anda wajib segera memberitahukan kami melalui `automation@flow-force.com` jika terjadi akses tidak sah atau pelanggaran keamanan.

                #### 3. Pengajuan Faktur dan Kebenaran Data
                - Vendor hanya boleh mengajukan faktur, pesanan pembelian (PO), surat jalan, dan faktur pajak yang asli, terverifikasi, dan sesuai dengan transaksi yang sebenarnya.
                - Pengajuan faktur palsu, ganda, rekayasa, atau diubah tanpa hak merupakan pelanggaran berat yang dapat mengakibatkan penutupan akun seketika dan tindakan hukum.

                #### 4. Penggunaan Sistem dan Batasan
                - Dilarang melakukan rekayasa balik (reverse engineering), memindai kerentanan sistem, atau membobol pembatas keamanan.
                - Dilarang melakukan manipulasi prompt AI (prompt injection), scraping otomatis, atau upaya mengakses dokumen milik vendor lain.
                - Fitur konversi penawaran harga internal dan alat AI dibatasi hanya untuk staf internal yang berwenang.

                #### 5. Pengakhiran dan Perubahan Ketentuan
                Kami berhak menangguhkan atau menghentikan akun yang melanggar ketentuan ini atau peraturan perundang-undangan di Indonesia. Kami dapat memperbarui ketentuan ini secara berkala. Penggunaan berkelanjutan setelah pemberitahuan dianggap sebagai persetujuan atas ketentuan baru.

                #### 6. Hukum yang Berlaku dan Penyelesaian Sengketa
                Syarat dan Ketentuan ini diatur dan ditafsirkan sesuai dengan hukum **Republik Indonesia**. Segala sengketa yang timbul akan diselesaikan melalui musyawarah mufakat atau melalui Pengadilan Negeri Jakarta Selatan.

                **Kontak:** Untuk pertanyaan mengenai ketentuan ini, hubungi `automation@flow-force.com`.
                """;
    }

    private String getDefaultPrivacyEn() {
        return """
                ### DRAFT – TO BE REVIEWED

                **Effective Date:** 09 November 2026 | **Version:** 1.0

                **PT Flow Force Indonesia** ("Company", "we", "us", or "our") respects your privacy and is committed to protecting the personal and corporate data of all vendors, partners, and portal users in compliance with the **Indonesian Personal Data Protection Law (Undang-Undang No. 27 Tahun 2022 tentang Pelindungan Data Pribadi / UU PDP)**.

                #### 1. Data Collected
                We collect information necessary to facilitate invoice processing, payment verification, and vendor administration:
                - **Corporate Information:** Company legal name, registered business address, company phone number, website.
                - **Tax and Banking Identifiers:** Nomor Pokok Wajib Pajak (NPWP / Tax ID), bank account numbers, bank branch details, and scanned corporate registration documents.
                - **Representative Personal Data:** Full name, corporate email address, telephone/mobile number, login credentials, and IP address.
                - **Transaction Documents:** Uploaded invoices (PDF/images), purchase orders, delivery receipts (Surat Jalan), tax receipts (Faktur Pajak), and supporting financial documents.

                #### 2. Purpose of Collection
                Your data is processed strictly for:
                - Authenticating your identity and authorizing portal access.
                - Processing, verifying, matching, and settling vendor invoice submissions.
                - Tax compliance, financial accounting, and auditing requirements under Indonesian law.
                - Generating notifications regarding ticket status updates, approvals, or required revisions.
                - Enhancing platform security, fraud prevention, and operational integrity.

                #### 3. Data Storage and Cloud Security
                - **Application & Database Storage:** User accounts, transaction metadata, audit logs, and settings are securely stored in our self-hosted, encrypted production database environment.
                - **Document Storage:** Uploaded invoice PDFs and image files are stored in **Cloudinary** using private, authenticated URLs with signature-token validation (`/image/authenticated/s--signature--/`). Direct public access to raw files is disallowed; access is brokered through our authenticated `/documents/...` endpoint.

                #### 4. Data Retention
                We retain vendor corporate and transaction records for a minimum of **ten (10) years** following the transaction date to comply with Indonesian statutory accounting and tax retention mandates (UU Ketentuan Umum dan Tata Cara Perpajakan). Account credentials and logs are retained for the duration of the commercial relationship plus applicable statutory limitation periods.

                #### 5. AI Processing by Google Gemini
                - **Usage Scope:** Google Gemini AI models are utilized strictly for internal staff workflows (such as optical text extraction from supplier quotations and natural-language ticket inquiry assistance).
                - **Privacy Safeguards:** In accordance with enterprise zero-retention guidelines, we **never** store user prompts or answer text in our permanent analytics databases — only anonymized metadata (token counts, execution duration, and feature categories) is tracked.
                - **No Model Training:** Data processed through our enterprise Gemini API endpoints is not used by Google to train or improve foundation models.

                #### 6. User Rights under Indonesian UU PDP
                Under UU PDP No. 27/2022, data subjects possess the right to:
                - Access, review, and request copies of their stored personal data.
                - Request rectification of inaccurate, incomplete, or outdated data.
                - Request cessation of processing or deletion of personal data, subject to mandatory legal and financial retention requirements.
                - Withdraw consent where processing relies on specific consent.

                #### 7. Data Protection Officer and Inquiries
                To exercise your rights or submit privacy-related questions, contact our Data Protection and Automation team:
                - **Email:** `automation@flow-force.com`
                - **Company:** PT Flow Force Indonesia
                - **Helpline:** +91-8972779588
                """;
    }

    private String getDefaultPrivacyId() {
        return """
                ### DRAF – UNTUK DITINJAU

                **Tanggal Berlaku:** 09 November 2026 | **Versi:** 1.0

                **PT Flow Force Indonesia** ("Perusahaan", "kami") menghormati privasi Anda dan berkomitmen melindungi data pribadi serta data korporat seluruh vendor, mitra, dan pengguna portal sesuai dengan **Undang-Undang Republik Indonesia No. 27 Tahun 2022 tentang Pelindungan Data Pribadi (UU PDP)**.

                #### 1. Data yang Dikumpulkan
                Kami mengumpulkan data yang diperlukan untuk verifikasi faktur, pemrosesan pembayaran, dan administrasi vendor:
                - **Data Perusahaan:** Nama resmi perusahaan, alamat terdaftar, nomor telepon perusahaan, situs web.
                - **Identitas Pajak dan Perbankan:** Nomor Pokok Wajib Pajak (NPWP), nomor rekening bank, kantor cabang bank, dan salinan dokumen pendaftaran perusahaan.
                - **Data Pribadi Perwakilan:** Nama lengkap, alamat email korporat, nomor telepon/HP, kredensial login, dan alamat IP.
                - **Dokumen Transaksi:** Faktur yang diunggah (PDF/gambar), pesanan pembelian (PO), surat jalan, faktur pajak, dan dokumen pendukung penagihan lainnya.

                #### 2. Tujuan Pengumpulan dan Pemrosesan
                Data Anda diproses semata-mata untuk:
                - Memverifikasi identitas dan memberikan izin akses portal.
                - Memproses, memverifikasi, mencocokkan, dan menyelesaikan penagihan faktur vendor.
                - Kepatuhan perpajakan, pembukuan keuangan, dan kebutuhan audit berdasarkan hukum Indonesia.
                - Mengirimkan pemberitahuan terkait status tiket, persetujuan, atau revisi yang diperlukan.
                - Menjaga keamanan sistem, pencegahan kecurangan, dan integritas operasional.

                #### 3. Penyimpanan Data dan Keamanan Cloud
                - **Penyimpanan Server & Database:** Kredensial akun, metadata transaksi, log audit, dan pengaturan disimpan secara aman di lingkungan basis data terenkripsi kami.
                - **Penyimpanan Dokumen:** Berkas faktur dan dokumen pendukung disimpan di **Cloudinary** menggunakan URL pribadi terautentikasi (`authenticated storage`) dengan segmen tanda tangan digital (`s--signature--`). Dokumen hanya dapat diakses melalui endpoint resmi `/documents/...` setelah verifikasi login dan hak akses.

                #### 4. Masa Retensi Data
                Kami menyimpan dokumen transaksi keuangan selama sekurang-kurangnya **sepuluh (10) tahun** terhitung sejak berakhirnya tahun buku terkait, guna memenuhi kewajiban perundang-undangan perpajakan dan pembukuan di Indonesia (UU KUP). Data akun aktif disimpan selama hubungan bisnis berlangsung ditambah masa daluwarsa hukum yang berlaku.

                #### 5. Pemrosesan AI oleh Google Gemini
                - **Ruang Lingkup:** Model AI Google Gemini digunakan khusus untuk operasional staf internal (seperti ekstraksi teks penawaran harga vendor dan asisten pencarian tiket).
                - **Perlindungan Privasi:** Sesuai prinsip pelindungan data, kami **tidak pernah menyimpan** teks prompt maupun teks jawaban di basis data pelacakan kami — hanya metadata anonim (jumlah token, waktu eksekusi, kategori fitur) yang dicatat untuk pemantauan biaya.
                - **Bukan Pelatihan Model:** Data yang dikirimkan melalui API enterprise Google Gemini tidak digunakan oleh Google untuk melatih model kecerdasan buatan.

                #### 6. Hak Subjek Data berdasarkan UU PDP
                Berdasarkan UU PDP No. 27/2022, Anda berhak untuk:
                - Mendapatkan akses dan meminta salinan atas data pribadi yang tersimpan.
                - Meminta perbaikan atau pembaruan atas data pribadi yang tidak akurat.
                - Meminta penghentian pemrosesan atau penghapusan data pribadi, sejauh tidak bertentangan dengan kewajiban retensi hukum dan keuangan.
                - Menarik kembali persetujuan yang sebelumnya telah diberikan.

                #### 7. Kontak dan Petugas Pelindungan Data
                Untuk melaksanakan hak-hak Anda atau mengajukan pertanyaan terkait privasi, hubungi tim kami:
                - **Email:** `automation@flow-force.com`
                - **Perusahaan:** PT Flow Force Indonesia
                - **Bantuan Telepon:** +91-8972779588
                """;
    }

    private String getDefaultCookiesEn() {
        return """
                ### DRAFT – TO BE REVIEWED

                **Effective Date:** 09 November 2026 | **Version:** 1.0

                #### 1. What Are Cookies?
                Cookies are small text files placed on your device when you browse websites. They are commonly used to remember your login session, security tokens, and user interface preferences.

                #### 2. Our Cookie Policy: Strictly Essential Only
                **InvoiceHub uses ONLY strictly essential technical cookies.**
                - We do **NOT** use tracking cookies, behavioral tracking pixels, advertising networks, or third-party marketing cookies.
                - We do **NOT** sell or monetize browsing data.

                #### 3. Actual Cookies Used by InvoiceHub
                Below is the comprehensive list of cookies set by the InvoiceHub portal:

                | Cookie Name | Purpose | Duration | Type |
                | :--- | :--- | :--- | :--- |
                | `JSESSIONID` | Maintains your authenticated session state and user identity securely while logged in. | Session (cleared upon closing browser or logout) | Strictly Essential |
                | `XSRF-TOKEN` / `_csrf` | Protects your account against Cross-Site Request Forgery (CSRF) attacks on form submissions. | Session | Security / Essential |
                | `lang` / `LOCALE` | Remembers your chosen display language preference (English or Bahasa Indonesia). | 1 Year | Preference / Functional |
                | `cookie_consent` | Remembers that you acknowledged the cookie information banner so it is not shown again. | 1 Year | Functional |
                | `theme` | Stores light/dark UI display preference if adjusted. | 1 Year | Functional |

                #### 4. Managing Cookies in Your Browser
                You can configure your browser to block or delete cookies at any time. However, because all cookies used by InvoiceHub are strictly essential for authentication and security, disabling them will prevent you from signing in or using the portal.

                **Questions:** Contact `automation@flow-force.com`.
                """;
    }

    private String getDefaultCookiesId() {
        return """
                ### DRAF – UNTUK DITINJAU

                **Tanggal Berlaku:** 09 November 2026 | **Versi:** 1.0

                #### 1. Apa Itu Cookie?
                Cookie adalah berkas teks kecil yang disimpan pada peramban (browser) perangkat Anda saat mengunjungi situs web. Cookie digunakan secara umum untuk mengingat sesi login, token keamanan, dan preferensi antarmuka pengguna.

                #### 2. Kebijakan Cookie Kami: Khusus Cookie Esensial
                **InvoiceHub HANYA menggunakan cookie teknis yang mutlak diperlukan (strictly essential).**
                - Kami **TIDAK** menggunakan cookie pelacak perilaku, piksel iklan, jaringan periklanan, atau pemasaran pihak ketiga.
                - Kami **TIDAK** menjual atau membagikan data penjelajahan Anda kepada pengiklan.

                #### 3. Daftar Cookie yang Digunakan InvoiceHub
                Berikut adalah daftar lengkap cookie yang disetel oleh sistem InvoiceHub:

                | Nama Cookie | Tujuan | Masa Berlaku | Kategori |
                | :--- | :--- | :--- | :--- |
                | `JSESSIONID` | Menjaga sesi login Anda tetap aman saat menggunakan portal. | Sesi (terhapus saat browser ditutup atau logout) | Mutlak Diperlukan |
                | `XSRF-TOKEN` / `_csrf` | Melindungi akun Anda dari serangan pemalsuan permintaan lintas situs (CSRF). | Sesi | Keamanan / Esensial |
                | `lang` / `LOCALE` | Mengingat preferensi bahasa tampilan yang Anda pilih (Bahasa Indonesia atau Inggris). | 1 Tahun | Preferensi / Fungsional |
                | `cookie_consent` | Mengingat bahwa Anda telah menyetujui pemberitahuan cookie sehingga tidak muncul kembali. | 1 Tahun | Fungsional |
                | `theme` | Menyimpan preferensi tema tampilan antarmuka (terang/gelap). | 1 Tahun | Fungsional |

                #### 4. Pengaturan Cookie pada Browser
                Anda dapat mengatur peramban Anda untuk memblokir atau menghapus cookie kapan saja. Namun, karena seluruh cookie pada InvoiceHub bersifat mutlak untuk keamanan dan autentikasi, memblokir cookie akan menyebabkan Anda tidak dapat masuk atau menggunakan portal.

                **Pertanyaan:** Hubungi `automation@flow-force.com`.
                """;
    }
}
