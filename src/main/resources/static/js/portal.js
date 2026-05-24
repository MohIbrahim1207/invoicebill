(function () {
    function $(selector) {
        return document.querySelector(selector);
    }

    function $all(selector) {
        return Array.from(document.querySelectorAll(selector));
    }

    function showToast(message, type) {
        var toastEl = $("#portalToast");
        var textEl = $("#portalToastText");
        if (!toastEl || !textEl || !window.bootstrap) return;

        toastEl.classList.remove("text-bg-primary", "text-bg-danger", "text-bg-success");
        toastEl.classList.add(type === "error" ? "text-bg-danger" : "text-bg-success");
        textEl.textContent = message;
        window.bootstrap.Toast.getOrCreateInstance(toastEl).show();
    }

    function bindFlashMessages() {
        var success = $("#flashSuccess");
        var error = $("#flashError");
        if (success && success.textContent.trim()) showToast(success.textContent.trim(), "success");
        if (error && error.textContent.trim()) showToast(error.textContent.trim(), "error");
    }

    function bindLoading(formId) {
        var form = $(formId);
        var overlay = $("#loadingOverlay");
        if (!form || !overlay) return;
        form.addEventListener("submit", function () {
            overlay.classList.remove("d-none");
        });
    }

    function bindFileDropzone() {
        var input = $("#invoiceFileInput");
        var dropzone = input ? input.closest(".file-dropzone") : null;
        var info = $("#fileChosenText");
        if (!input || !dropzone) return;

        input.addEventListener("change", function () {
            info.textContent = input.files && input.files.length ? input.files[0].name : "PDF, PNG, JPG, JPEG, WEBP";
        });

        ["dragenter", "dragover"].forEach(function (evt) {
            dropzone.addEventListener(evt, function (e) {
                e.preventDefault();
                dropzone.classList.add("dragover");
            });
        });

        ["dragleave", "drop"].forEach(function (evt) {
            dropzone.addEventListener(evt, function (e) {
                e.preventDefault();
                dropzone.classList.remove("dragover");
            });
        });

        dropzone.addEventListener("drop", function (e) {
            if (e.dataTransfer.files && e.dataTransfer.files.length) {
                input.files = e.dataTransfer.files;
                info.textContent = e.dataTransfer.files[0].name;
            }
        });
    }

    function bindTableSearch(tableId, inputId) {
        var table = $(tableId);
        var input = $(inputId);
        if (!table || !input) return;

        input.addEventListener("input", function () {
            var term = input.value.toLowerCase();
            $all(tableId + " tbody tr").forEach(function (row) {
                if (row.classList.contains("table-empty-row")) return;
                var hay = (row.getAttribute("data-search") || "").toLowerCase();
                row.style.display = hay.indexOf(term) >= 0 ? "" : "none";
            });
            refreshTableMeta();
        });
    }

    function bindStatusFilter() {
        var select = $("#statusFilterInput");
        var rows = $all("#billingTable tbody tr");
        if (!select || !rows.length) return;

        select.addEventListener("change", function () {
            var term = select.value.toLowerCase();
            rows.forEach(function (row) {
                if (row.classList.contains("table-empty-row")) return;
                var badge = row.querySelector("td:nth-child(6) .badge, td:nth-child(6) .status-badge");
                var statusText = badge ? badge.textContent.toLowerCase() : "";
                row.style.display = !term || statusText === term ? "" : "none";
            });
            refreshTableMeta();
        });
    }

    var currentPage = 1;
    var pageSize = 6;

    function visibleRows() {
        return $all("#billingTable tbody tr").filter(function (row) {
            return !row.classList.contains("table-empty-row") && row.style.display !== "none";
        });
    }

    function applyPagination() {
        var rows = visibleRows();
        if (!rows.length) return;

        var start = (currentPage - 1) * pageSize;
        var end = start + pageSize;
        rows.forEach(function (row, index) {
            row.style.visibility = index >= start && index < end ? "visible" : "collapse";
        });

        var prev = $("#prevPageBtn");
        var next = $("#nextPageBtn");
        if (prev) prev.disabled = currentPage === 1;
        if (next) next.disabled = end >= rows.length;
    }

    function refreshTableMeta() {
        var rows = visibleRows();
        var count = $("#tableCountText");
        if (count) count.textContent = "Showing " + rows.length + " record(s)";
        currentPage = 1;
        applyPagination();
        updateStatsAndChart();
    }

    function bindPager() {
        var prev = $("#prevPageBtn");
        var next = $("#nextPageBtn");
        if (!prev || !next) return;

        prev.addEventListener("click", function () {
            if (currentPage > 1) {
                currentPage -= 1;
                applyPagination();
            }
        });
        next.addEventListener("click", function () {
            var rows = visibleRows();
            if (currentPage * pageSize < rows.length) {
                currentPage += 1;
                applyPagination();
            }
        });
    }

    function bindHistoryModal() {
        if (!window.bootstrap) return;
        var modalEl = $("#historyModal");
        if (!modalEl) return;

        var modal = window.bootstrap.Modal.getOrCreateInstance(modalEl);
        $all(".js-history-btn").forEach(function (btn) {
            btn.addEventListener("click", function () {
                var invoice = btn.getAttribute("data-invoice") || "-";
                var client = btn.getAttribute("data-client") || "-";
                var meta = $("#historyModalMeta");
                if (meta) meta.textContent = "Invoice " + invoice + " | Client: " + client;
                modal.show();
            });
        });
    }

    var chartRef = null;
    var sparkRefs = {};
    function updateStatsAndChart() {
        var rows = visibleRows();
        var total = rows.length;
        var pending = 0;
        var approved = 0;
        var revised = 0;
        var released = 0;
        var amount = 0;

        rows.forEach(function (row) {
            amount += parseFloat(row.getAttribute("data-amount") || "0");
            var badge = row.querySelector("td:nth-child(6) .badge, td:nth-child(6) .status-badge");
            var status = badge ? badge.textContent.trim() : "";
            if (status === "Pending") pending += 1;
            if (status === "Approved") approved += 1;
            if (status === "Revised") revised += 1;
            if (status === "Released") released += 1;
        });

        var totalEl = $("#statTotal");
        var pendingEl = $("#statPending");
        var approvedEl = $("#statApproved");
        var amountEl = $("#statAmount");
        if (totalEl) totalEl.textContent = total;
        if (pendingEl) pendingEl.textContent = pending;
        if (approvedEl) approvedEl.textContent = approved;
        if (amountEl) amountEl.textContent = amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });

        // Update table summary footer if present
        var summaryCount = $("#summaryCount");
        var summaryAmount = $("#summaryAmount");
        var summaryStatusCounts = $("#summaryStatusCounts");
        if (summaryCount) summaryCount.textContent = total;
        if (summaryAmount) summaryAmount.textContent = amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        if (summaryStatusCounts) summaryStatusCounts.textContent = "P: " + pending + " • A: " + approved + " • R: " + revised + " • L: " + released;

        var canvas = $("#invoiceChart");
        if (!canvas || !window.Chart) return;

        var chartData = [pending, approved, revised, released];
        if (chartRef) {
            chartRef.data.datasets[0].data = chartData;
            chartRef.update();
            return;
        }

        chartRef = new window.Chart(canvas, {
            type: "doughnut",
            data: {
                labels: ["Pending", "Approved", "Revised", "Released"],
                datasets: [{
                    data: chartData,
                    backgroundColor: ["#f7b84b", "#45cb85", "#f06578", "#5b8def"],
                    borderWidth: 1
                }]
            },
            options: {
                plugins: {
                    legend: { position: "bottom" }
                },
                cutout: "68%"
            }
        });
    }

    // Render small sparklines inside stat cards. Each canvas has data-target attribute mapped to stat names.
    function renderSparklines() {
        if (!window.Chart) return;
        var canvases = Array.from(document.querySelectorAll('.sparkline-canvas'));
        canvases.forEach(function (c) {
            var key = c.getAttribute('data-target') || 'total';
            var type = c.getAttribute('data-type') || 'bar';
            // derive a tiny trend from visible rows: simple moving distribution over 6 periods
            var rows = visibleRows();
            var values = [0,0,0,0,0,0];
            rows.forEach(function (r, i) {
                var idx = i % values.length;
                var amt = parseFloat(r.getAttribute('data-amount') || '0');
                if (key === 'amount') values[idx] += amt;
                else {
                    var badge = r.querySelector('td:nth-child(6) .badge, td:nth-child(6) .status-badge');
                    var status = badge ? badge.textContent.trim() : '';
                    if ((key === 'pending' && status === 'Pending') || (key === 'approved' && status === 'Approved') || (key === 'total')) {
                        values[idx] += 1;
                    }
                }
            });

            // normalize values to integers
            values = values.map(function (v) { return Math.round(v); });

            // reuse existing chart instance if present
            if (sparkRefs[key]) {
                sparkRefs[key].data.datasets[0].data = values;
                sparkRefs[key].update();
                return;
            }

            sparkRefs[key] = new Chart(c.getContext('2d'), {
                type: type,
                data: {
                    labels: ['','','','','',''],
                    datasets: [{
                        data: values,
                        backgroundColor: type === 'bar' ? '#dbeafe' : 'rgba(43,110,242,0.08)',
                        borderColor: '#2b6ef2',
                        borderWidth: 1,
                        fill: false,
                        tension: 0.3,
                        pointRadius: 0
                    }]
                },
                options: {
                    maintainAspectRatio: false,
                    responsive: true,
                    plugins: { legend: { display: false } },
                    scales: { x: { display: false }, y: { display: false } }
                }
            });
        });
    }

    bindFlashMessages();
    bindLoading("#invoiceUploadForm");
    bindLoading("#clientForm");
    bindFileDropzone();
    bindTableSearch("#billingTable", "#invoiceSearchInput");
    bindTableSearch("#clientTable", "#clientSearchInput");
    bindStatusFilter();
    bindPager();
    bindHistoryModal();
    refreshTableMeta();
    // initial sparkline render and keep updating when table state changes
    renderSparklines();
    // watch for table changes and re-render
    var observer = new MutationObserver(function () { renderSparklines(); });
    var tableBody = document.querySelector('#billingTable tbody');
    if (tableBody) observer.observe(tableBody, { childList: true, subtree: true });
})();

