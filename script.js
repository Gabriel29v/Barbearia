const configuredApiUrl = (window.BARBER_API_BASE_URL || '').trim().replace(/\/+$/, '');
const API_BASE = `${configuredApiUrl}/api`;
const TOKEN_KEY = 'penteFinoSession';
const slotsElement = document.getElementById('timeSlots');
const dateInput = document.getElementById('bookingDate');
const serviceSelect = document.getElementById('serviceSelect');
const selectedTimeInput = document.getElementById('selectedTime');
const bookingSubmit = document.getElementById('bookingSubmit');
let currentUser = null;
let calendarMonth = new Date();
let selectedAdminDate = localDateString(new Date());
let appointmentDates = new Set();
let knownAppointmentIds = new Set();
let firstAdminLoad = true;
let loginMode = 'customer';
let datePickerMonth = new Date();

function localDateString(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

function showMessage(element, message, isError = false) {
    element.textContent = message;
    element.classList.remove('hidden', 'message-error', 'message-success');
    element.classList.add(isError ? 'message-error' : 'message-success');
}

async function apiRequest(path, options = {}) {
    const headers = new Headers(options.headers || {});
    headers.set('Content-Type', 'application/json');
    const token = localStorage.getItem(TOKEN_KEY);
    if (token) headers.set('Authorization', `Bearer ${token}`);
    const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) {
        throw new Error(payload.error
            || `A API respondeu com HTTP ${response.status}, mas não enviou uma mensagem de erro. Confira os logs e a configuração do serviço.`);
    }
    return payload;
}

function setAccountView(user) {
    currentUser = user;
    const accountForms = document.getElementById('accountForms');
    const accountStatus = document.getElementById('accountStatus');
    const bookingPrompt = document.getElementById('bookingAccountPrompt');
    const adminPanel = document.getElementById('adminPanel');
    const customerLinks = document.querySelectorAll('[data-customer-link]');
    const customerTabs = document.getElementById('customerViewTabs');

    if (accountForms) accountForms.classList.toggle('hidden', Boolean(user));
    if (accountStatus) {
        accountStatus.replaceChildren();
        accountStatus.classList.toggle('hidden', !user);
    }
    if (bookingPrompt) bookingPrompt.classList.toggle('hidden', Boolean(user && user.role === 'customer'));
    if (adminPanel) adminPanel.classList.toggle('hidden', !user || user.role !== 'admin');
    customerLinks.forEach(link => link.classList.toggle('hidden', !user || user.role !== 'customer'));
    if (customerTabs) customerTabs.classList.toggle('hidden', !user || user.role !== 'customer');

    if (!user) {
        document.getElementById('bookingForm')?.classList.remove('hidden');
        document.getElementById('customerAppointmentsPanel')?.classList.add('hidden');
        document.getElementById('customerAppointments')?.replaceChildren();
        return;
    }

    if (accountStatus) {
        const text = document.createElement('span');
        text.textContent = user.role === 'admin'
            ? `Administrador conectado: ${user.name} (${user.email}).`
            : `Olá, ${user.name}! Agende abaixo usando sua conta (${user.email}).`;
        const logout = document.createElement('button');
        logout.type = 'button';
        logout.className = 'ml-3 font-semibold text-brand-gold underline';
        logout.textContent = 'Sair';
        logout.addEventListener('click', logoutUser);
        accountStatus.append(text, logout);
    }

    if (user.role === 'admin' && adminPanel) {
        refreshAdminCalendar();
        if (!window.adminRefreshTimer) {
            window.adminRefreshTimer = window.setInterval(refreshAdminCalendar, 30000);
        }
    }
}

async function logoutUser() {
    try {
        await apiRequest('/logout', { method: 'POST', body: '{}' });
    } catch (error) {
        console.error('Falha ao encerrar a sessão:', error);
    }
    localStorage.removeItem(TOKEN_KEY);
    setAccountView(null);
}

function switchAccountTab(tab) {
    document.querySelectorAll('[data-account-tab]').forEach(button => {
        const active = button.dataset.accountTab === tab;
        button.classList.toggle('active', active);
        button.setAttribute('aria-selected', String(active));
    });
    const loginSelected = tab !== 'register';
    loginMode = tab === 'admin' ? 'admin' : 'customer';
    document.getElementById('loginForm').classList.toggle('hidden', !loginSelected);
    document.getElementById('registerForm').classList.toggle('hidden', tab !== 'register');
    document.getElementById('loginHeading').textContent = loginMode === 'admin'
        ? 'Entrar como administrador'
        : 'Entrar como cliente';
    document.getElementById('loginHelp').textContent = loginMode === 'admin'
        ? 'Use o e-mail e a senha de administrador configurados ao iniciar o servidor Java.'
        : 'Use o e-mail e a senha da sua conta de cliente.';
    document.getElementById('accountStatus').classList.add('hidden');
}

function getServiceDuration() {
    const match = serviceSelect.value.match(/\((\d+)\s*min\)\s*$/);
    return match ? Number(match[1]) : 0;
}

function resetTimeSelection() {
    selectedTimeInput.value = '';
    bookingSubmit.disabled = true;
}

function renderDatePicker() {
    const year = datePickerMonth.getFullYear();
    const month = datePickerMonth.getMonth();
    const today = localDateString(new Date());
    const days = document.getElementById('datePickerDays');
    const previousMonth = new Date(year, month - 1, 1);
    document.getElementById('datePickerHeading').textContent =
        new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' }).format(datePickerMonth);
    document.getElementById('datePickerPrevious').disabled =
        previousMonth.getFullYear() < new Date().getFullYear()
        || (previousMonth.getFullYear() === new Date().getFullYear()
            && previousMonth.getMonth() < new Date().getMonth());
    days.replaceChildren();

    const firstDayOffset = (new Date(year, month, 1).getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    for (let i = 0; i < firstDayOffset; i += 1) {
        const empty = document.createElement('span');
        empty.className = 'date-picker-empty';
        empty.setAttribute('aria-hidden', 'true');
        days.appendChild(empty);
    }

    for (let day = 1; day <= daysInMonth; day += 1) {
        const date = dateFromParts(year, month, day);
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'date-picker-day';
        button.textContent = String(day);
        button.disabled = date < today;
        button.classList.toggle('selected', dateInput.value === date);
        button.setAttribute('aria-pressed', String(dateInput.value === date));
        button.setAttribute('aria-label', new Intl.DateTimeFormat('pt-BR', {
            dateStyle: 'full'
        }).format(new Date(`${date}T12:00:00`)));
        button.addEventListener('click', () => {
            dateInput.value = date;
            document.getElementById('bookingDateLabel').textContent =
                new Intl.DateTimeFormat('pt-BR').format(new Date(`${date}T12:00:00`));
            dateInput.dispatchEvent(new Event('change', { bubbles: true }));
            document.getElementById('datePickerPanel').classList.add('hidden');
            document.getElementById('datePickerButton').setAttribute('aria-expanded', 'false');
            document.getElementById('datePickerButton').focus();
        });
        days.appendChild(button);
    }
}

async function loadAvailableTimes() {
    slotsElement.replaceChildren();
    resetTimeSelection();
    if (!dateInput.value || !serviceSelect.value) {
        slotsElement.textContent = 'Escolha um serviço e um dia para consultar horários.';
        return;
    }

    slotsElement.textContent = 'Consultando horários...';
    try {
        const query = new URLSearchParams({
            date: dateInput.value,
            duration: String(getServiceDuration())
        });
        const { slots } = await apiRequest(`/availability?${query}`);
        slotsElement.replaceChildren();
        if (slots.length === 0) {
            slotsElement.textContent = 'Não há horários disponíveis para este dia.';
            return;
        }
        slots.forEach(time => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'slot-btn';
            button.textContent = time;
            button.setAttribute('aria-pressed', 'false');
            button.addEventListener('click', () => {
                slotsElement.querySelectorAll('.slot-btn').forEach(slot => {
                    slot.classList.remove('selected');
                    slot.setAttribute('aria-pressed', 'false');
                });
                button.classList.add('selected');
                button.setAttribute('aria-pressed', 'true');
                selectedTimeInput.value = time;
                bookingSubmit.disabled = false;
            });
            slotsElement.appendChild(button);
        });
    } catch (error) {
        slotsElement.textContent = error.message;
    }
}

async function submitBooking(event) {
    event.preventDefault();
    if (!currentUser || currentUser.role !== 'customer') {
        window.location.href = 'login.html';
        return;
    }

    bookingSubmit.disabled = true;
    try {
        await apiRequest('/appointments', {
            method: 'POST',
            body: JSON.stringify({
                service: serviceSelect.value,
                date: dateInput.value,
                time: selectedTimeInput.value,
                duration: getServiceDuration()
            })
        });
        showMessage(document.getElementById('bookingNotice'),
            'Agendamento confirmado! Você pode consultá-lo na aba Meus agendamentos.');
        await loadAvailableTimes();
        document.getElementById('bookingNotice').scrollIntoView({ behavior: 'smooth', block: 'center' });
    } catch (error) {
        showMessage(document.getElementById('bookingNotice'), error.message, true);
        await loadAvailableTimes();
    } finally {
        bookingSubmit.disabled = !selectedTimeInput.value;
    }
}

async function loadCustomerAppointments() {
    const container = document.getElementById('customerAppointments');
    const notice = document.getElementById('customerAppointmentsNotice');
    if (!container || !currentUser || currentUser.role !== 'customer') return;

    notice.classList.add('hidden');
    container.textContent = 'Carregando seus agendamentos...';
    try {
        const { appointments } = await apiRequest('/my/appointments');
        container.replaceChildren();
        if (appointments.length === 0) {
            container.textContent = 'Você ainda não possui agendamentos.';
            return;
        }

        appointments.forEach(appointment => {
            const card = document.createElement('article');
            card.className = 'appointment-card';
            const title = document.createElement('h3');
            title.className = 'font-semibold text-white';
            const date = new Date(`${appointment.date}T12:00:00`);
            title.textContent = `${new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(date)} às ${appointment.time}`;
            const detail = document.createElement('p');
            detail.className = 'text-sm text-brand-muted mt-1';
            detail.textContent = `${appointment.service} · ${appointment.duration} minutos`;
            card.append(title, detail);
            container.appendChild(card);
        });
    } catch (error) {
        container.replaceChildren();
        showMessage(notice, error.message, true);
    }
}

function switchCustomerView(tab) {
    if (!currentUser || currentUser.role !== 'customer') return;
    const showAppointments = tab === 'appointments';
    document.getElementById('bookingForm').classList.toggle('hidden', showAppointments);
    document.getElementById('customerAppointmentsPanel').classList.toggle('hidden', !showAppointments);
    document.querySelectorAll('[data-customer-tab]').forEach(button => {
        const active = button.dataset.customerTab === tab;
        button.classList.toggle('active', active);
        button.setAttribute('aria-selected', String(active));
    });
    if (showAppointments) loadCustomerAppointments();
}

function monthLabel(date) {
    return new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' }).format(date);
}

function dateFromParts(year, month, day) {
    const date = new Date(year, month, day);
    return localDateString(date);
}

async function refreshAdminCalendar() {
    if (!currentUser || currentUser.role !== 'admin' || !document.getElementById('adminPanel')) return;
    const year = calendarMonth.getFullYear();
    const month = calendarMonth.getMonth();
    const start = dateFromParts(year, month, 1);
    const end = dateFromParts(year, month + 1, 1);
    try {
        const query = new URLSearchParams({ from: start, to: end });
        const { appointments } = await apiRequest(`/admin/appointments?${query}`);
        renderAdminCalendar(appointments);
        renderAppointmentsForDay(appointments, selectedAdminDate);

        const latestIds = new Set(appointments.map(appointment => appointment.id));
        const newAppointments = appointments.filter(appointment =>
            !firstAdminLoad && !knownAppointmentIds.has(appointment.id)
        );
        knownAppointmentIds = latestIds;
        if (newAppointments.length > 0 && 'Notification' in window && Notification.permission === 'granted') {
            newAppointments.forEach(appointment => new Notification('Novo agendamento', {
                body: `${appointment.clientName} — ${appointment.service}, ${appointment.date} às ${appointment.time}.`
            }));
        }
        firstAdminLoad = false;
    } catch (error) {
        showMessage(document.getElementById('adminNotice'), error.message, true);
    }
}

function renderAdminCalendar(appointments) {
    const year = calendarMonth.getFullYear();
    const month = calendarMonth.getMonth();
    document.getElementById('calendarHeading').textContent = monthLabel(calendarMonth);
    appointmentDates = new Set(appointments.map(appointment => appointment.date));
    const grid = document.getElementById('adminCalendar');
    grid.replaceChildren();
    const firstDayOffset = (new Date(year, month, 1).getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    for (let i = 0; i < firstDayOffset; i += 1) {
        const empty = document.createElement('span');
        empty.className = 'calendar-empty';
        empty.setAttribute('aria-hidden', 'true');
        grid.appendChild(empty);
    }
    for (let day = 1; day <= daysInMonth; day += 1) {
        const date = dateFromParts(year, month, day);
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'calendar-day';
        button.classList.toggle('has-appointments', appointmentDates.has(date));
        button.classList.toggle('selected', selectedAdminDate === date);
        button.textContent = String(day);
        button.setAttribute('aria-label', appointmentDates.has(date)
            ? `${day}: há agendamentos`
            : String(day));
        button.setAttribute('aria-pressed', String(selectedAdminDate === date));
        button.addEventListener('click', () => {
            selectedAdminDate = date;
            renderAdminCalendar(appointments);
            renderAppointmentsForDay(appointments, date);
        });
        grid.appendChild(button);
    }
}

function renderAppointmentsForDay(appointments, date) {
    const container = document.getElementById('adminAppointments');
    const selected = appointments
        .filter(appointment => appointment.date === date)
        .sort((a, b) => a.time.localeCompare(b.time));
    const parsedDate = new Date(`${date}T12:00:00`);
    document.getElementById('appointmentsHeading').textContent =
        `Agendamentos — ${new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(parsedDate)}`;
    container.replaceChildren();
    if (selected.length === 0) {
        container.textContent = 'Nenhum agendamento para este dia.';
        return;
    }
    selected.forEach(appointment => {
        const card = document.createElement('article');
        card.className = 'appointment-card';
        const title = document.createElement('h4');
        title.className = 'font-semibold text-white';
        title.textContent = `${appointment.time} — ${appointment.clientName}`;
        const detail = document.createElement('p');
        detail.className = 'text-sm text-brand-muted mt-1';
        detail.textContent = `${appointment.service} · ${appointment.clientEmail} · ${appointment.clientPhone}`;
        card.append(title, detail);
        container.appendChild(card);
    });
}

document.addEventListener('DOMContentLoaded', async () => {
    if (dateInput) {
        const today = localDateString(new Date());
        dateInput.value = today;
        datePickerMonth = new Date();
        document.getElementById('bookingDateLabel').textContent =
            new Intl.DateTimeFormat('pt-BR').format(new Date(`${today}T12:00:00`));
        renderDatePicker();
        document.getElementById('datePickerButton').addEventListener('click', () => {
            const panel = document.getElementById('datePickerPanel');
            const isOpening = panel.classList.contains('hidden');
            panel.classList.toggle('hidden', !isOpening);
            document.getElementById('datePickerButton').setAttribute('aria-expanded', String(isOpening));
            if (isOpening) renderDatePicker();
        });
        document.getElementById('datePickerPrevious').addEventListener('click', () => {
            datePickerMonth = new Date(datePickerMonth.getFullYear(), datePickerMonth.getMonth() - 1, 1);
            renderDatePicker();
        });
        document.getElementById('datePickerNext').addEventListener('click', () => {
            datePickerMonth = new Date(datePickerMonth.getFullYear(), datePickerMonth.getMonth() + 1, 1);
            renderDatePicker();
        });
        document.addEventListener('keydown', event => {
            if (event.key === 'Escape') {
                document.getElementById('datePickerPanel').classList.add('hidden');
                document.getElementById('datePickerButton').setAttribute('aria-expanded', 'false');
            }
        });
        serviceSelect.addEventListener('change', loadAvailableTimes);
        dateInput.addEventListener('change', loadAvailableTimes);
        document.getElementById('bookingForm').addEventListener('submit', submitBooking);
        document.querySelectorAll('[data-customer-tab]').forEach(button => {
            button.addEventListener('click', () => switchCustomerView(button.dataset.customerTab));
        });
        document.getElementById('refreshCustomerAppointments').addEventListener('click', loadCustomerAppointments);
        document.querySelectorAll('[data-customer-link]').forEach(link => {
            link.addEventListener('click', () => switchCustomerView('appointments'));
        });
    }

    if (document.getElementById('loginForm')) {
        document.querySelectorAll('[data-account-tab]').forEach(button => {
            button.addEventListener('click', () => switchAccountTab(button.dataset.accountTab));
        });
        document.getElementById('loginForm').addEventListener('submit', async event => {
            event.preventDefault();
            const form = event.currentTarget;
            const status = document.getElementById('accountStatus');
            try {
                const result = await apiRequest('/login', {
                    method: 'POST',
                    body: JSON.stringify({
                        email: form.elements.email.value,
                        password: form.elements.password.value
                    })
                });
                if (result.user.role !== loginMode) {
                    throw new Error(loginMode === 'admin'
                        ? 'Essas credenciais não pertencem à conta do administrador.'
                        : 'Essa é uma conta de administrador. Selecione a aba Administrador para entrar.');
                }
                localStorage.setItem(TOKEN_KEY, result.token);
                if (result.user.role === 'customer') {
                    window.location.assign('index.html');
                    return;
                }
                setAccountView(result.user);
                form.reset();
                document.getElementById('adminPanel').scrollIntoView({ behavior: 'smooth' });
            } catch (error) {
                showMessage(status, error.message, true);
            }
        });

        document.getElementById('registerForm').addEventListener('submit', async event => {
            event.preventDefault();
            const form = event.currentTarget;
            try {
                const result = await apiRequest('/register', {
                    method: 'POST',
                    body: JSON.stringify({
                        name: form.elements.name.value,
                        email: form.elements.email.value,
                        phone: form.elements.phone.value,
                        password: form.elements.password.value
                    })
                });
                localStorage.setItem(TOKEN_KEY, result.token);
                window.location.assign('index.html');
            } catch (error) {
                showMessage(document.getElementById('accountStatus'), error.message, true);
            }
        });
    }

    if (document.getElementById('adminPanel')) {
        document.getElementById('calendarPrevious').addEventListener('click', () => {
            calendarMonth = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() - 1, 1);
            selectedAdminDate = dateFromParts(calendarMonth.getFullYear(), calendarMonth.getMonth(), 1);
            firstAdminLoad = true;
            refreshAdminCalendar();
        });
        document.getElementById('calendarNext').addEventListener('click', () => {
            calendarMonth = new Date(calendarMonth.getFullYear(), calendarMonth.getMonth() + 1, 1);
            selectedAdminDate = dateFromParts(calendarMonth.getFullYear(), calendarMonth.getMonth(), 1);
            firstAdminLoad = true;
            refreshAdminCalendar();
        });
        document.getElementById('notificationPermission').addEventListener('click', async () => {
            if (!('Notification' in window)) {
                showMessage(document.getElementById('adminNotice'), 'Este navegador não oferece notificações do sistema.', true);
                return;
            }
            const permission = await Notification.requestPermission();
            showMessage(document.getElementById('adminNotice'), permission === 'granted'
                ? 'Notificações de novos agendamentos ativadas.'
                : 'Permissão de notificação não concedida.', permission !== 'granted');
        });
    }

    const token = localStorage.getItem(TOKEN_KEY);
    if (token) {
        try {
            const result = await apiRequest('/me');
            setAccountView(result.user);
        } catch {
            localStorage.removeItem(TOKEN_KEY);
            setAccountView(null);
        }
    }
    if (dateInput) await loadAvailableTimes();
});
