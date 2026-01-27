/**
 * UI Controller - Manages screen transitions and data rendering
 */

class UIController {
    constructor() {
        this.currentScreen = 'splash';
    }

    /**
     * Show a specific screen
     * @param {string} screenId - 'splash', 'error', 'bill-statement', 'payment-history'
     */
    showScreen(screenId) {
        console.log('showScreen called with:', screenId);
        // Hide all screens
        document.querySelectorAll('.screen').forEach(screen => {
            screen.classList.remove('active');
        });

        // Show target screen
        const targetScreen = document.getElementById(`${screenId}-screen`);
        if (targetScreen) {
            targetScreen.classList.add('active');
            this.currentScreen = screenId;
            console.log('Screen switched to:', screenId, 'Element found:', !!targetScreen);
        } else {
            console.error('Target screen not found:', `${screenId}-screen`);
            // Fallback: try to show error screen
            const errorScreen = document.getElementById('error-screen');
            if (errorScreen) {
                errorScreen.classList.add('active');
                const errorMsg = document.getElementById('error-message');
                if (errorMsg) {
                    errorMsg.textContent = `Screen '${screenId}' not found in DOM`;
                }
            }
        }
    }

    /**
     * Show error screen with message
     * @param {string} message
     */
    showError(message) {
        const errorMessageEl = document.getElementById('error-message');
        if (errorMessageEl) {
            errorMessageEl.textContent = message;
        }
        this.showScreen('error');
    }

    /**
     * Render bill statement data
     * @param {BillStatementModel} statement
     */
    renderBillStatement(statement) {
        console.log('renderBillStatement called with:', statement);
        try {
            // Amount card
            const amountEl = document.getElementById('bill-amount');
            const dueDateEl = document.getElementById('bill-due-date');
            const billerNameEl = document.getElementById('bill-biller-name');
            const unitsEl = document.getElementById('bill-units');
            
            if (!amountEl || !dueDateEl || !billerNameEl || !unitsEl) {
                throw new Error('Required DOM elements not found for bill statement');
            }
            
            amountEl.textContent = `₹${statement.amountDue || '0.00'}`;
            dueDateEl.textContent = `Due Date: ${statement.dueDate || '--'}`;
            billerNameEl.textContent = statement.billerName || '--';
            unitsEl.textContent = statement.unitsConsumed || statement.units_consumed || 'N/A';

        // Consumption info
        document.getElementById('bill-avg-units').textContent = statement.monthlyAverageUnits;
        document.getElementById('bill-highest-month').textContent = statement.highestConsumptionMonth;
        document.getElementById('bill-highest-unit').textContent = statement.highestConsumptionUnit;

        // Biller info
        document.getElementById('bill-biller-id').textContent = statement.billerId;
        document.getElementById('bill-biller-name-info').textContent = statement.billerName;
        document.getElementById('bill-tariff').textContent = `₹ ${statement.tariffRate} per unit`;

            // Consumer info
            document.getElementById('bill-consumer-name').textContent = statement.consumerName || '--';
            document.getElementById('bill-consumer-number').textContent = statement.consumerNumber || '--';

            console.log('All bill statement fields populated, switching screen...');
            this.showScreen('bill-statement');
        } catch (error) {
            console.error('Error rendering bill statement:', error);
            this.showError('Failed to render bill statement: ' + error.message);
        }
    }

    /**
     * Render payment history data
     * @param {PaymentHistoryModel} history
     */
    renderPaymentHistory(history) {
        // Last payment card
        const latest = history.payments[0];
        if (latest) {
            document.getElementById('payment-last-amount').textContent = `₹${latest.paymentAmount}`;
            document.getElementById('payment-last-date').textContent = `Paid On: ${latest.paymentDate}`;
        }
        document.getElementById('payment-biller-name').textContent = history.billerName;
        document.getElementById('payment-units').textContent = history.unitsConsumed || 'N/A';

        // Payment list
        const paymentListEl = document.getElementById('payment-list');
        paymentListEl.innerHTML = '';

        if (history.payments.length === 0) {
            paymentListEl.innerHTML = '<div class="info-row"><span class="info-value">No payments found.</span></div>';
        } else {
            history.payments.forEach((payment, index) => {
                const paymentItem = document.createElement('div');
                paymentItem.className = 'payment-item';
                
                paymentItem.innerHTML = `
                    <div class="payment-item-content">
                        <div class="payment-item-left">
                            <div class="payment-date">${payment.paymentDate}</div>
                            <div class="payment-details">
                                <div>Amount:</div>
                                <div>Txn: ${payment.transactionId}</div>
                                <div>Status: ${payment.status}</div>
                                ${payment.paymentVia ? `<div>Paid via: ${payment.paymentVia}</div>` : ''}
                            </div>
                        </div>
                        <div class="payment-item-right">
                            <div class="payment-amount">₹ ${payment.paymentAmount}</div>
                        </div>
                    </div>
                    ${index < history.payments.length - 1 ? '<div class="payment-divider"></div>' : ''}
                `;
                
                paymentListEl.appendChild(paymentItem);
            });
        }

        // Consumer info
        document.getElementById('payment-consumer-name').textContent = history.consumerName;
        document.getElementById('payment-consumer-number').textContent = history.consumerNumber;

        // Biller info
        document.getElementById('payment-biller-id').textContent = history.billerId;
        document.getElementById('payment-biller-name-info').textContent = history.billerName;
        document.getElementById('payment-units-info').textContent = history.unitsConsumed || 'N/A';
        document.getElementById('payment-statement-id').textContent = history.statementId;

        this.showScreen('payment-history');
    }
}

// Global UI controller instance
const uiController = new UIController();
