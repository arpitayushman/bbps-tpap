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
     * Uses secure rendering to obfuscate sensitive data in DOM
     * Supports both electricity bills and credit card statements
     * @param {BillStatementModel} statement
     */
    renderBillStatement(statement) {
        console.log('renderBillStatement called with:', statement);
        try {
            // Detect if this is a credit card statement
            const isCreditCard = this.isCreditCardStatement(statement);
            console.log('Statement type detected:', isCreditCard ? 'CREDIT_CARD' : 'ELECTRICITY');
            
            // Update header title based on statement type
            const headerTitleEl = document.querySelector('#bill-statement-screen .header-title');
            if (headerTitleEl) {
                headerTitleEl.textContent = isCreditCard ? 'Credit Card Statement' : 'Bill Statement';
            }
            
            // Amount card - use secure rendering
            const amountEl = document.getElementById('bill-amount');
            const dueDateEl = document.getElementById('bill-due-date');
            const billerNameEl = document.getElementById('bill-biller-name');
            const unitsEl = document.getElementById('bill-units');
            
            if (!amountEl || !dueDateEl || !billerNameEl || !unitsEl) {
                throw new Error('Required DOM elements not found for bill statement');
            }
            
            // Use secure rendering for all sensitive data
            const amountText = `₹${statement.amountDue || '0.00'}`;
            securityModule.secureSetTextContent(amountEl, amountText);
            
            const dueDateText = `Due Date: ${statement.dueDate || '--'}`;
            securityModule.secureSetTextContent(dueDateEl, dueDateText);
            
            securityModule.secureSetTextContent(billerNameEl, statement.billerName || '--');
            
            // For credit cards, show "Card Number" instead of "Units Consumed"
            const unitsLabelEl = document.querySelector('#bill-statement-screen .footer-item:last-child .footer-label');
            if (unitsLabelEl) {
                unitsLabelEl.textContent = isCreditCard ? 'CARD NUMBER' : 'UNITS CONSUMED';
            }
            
            if (isCreditCard) {
                securityModule.secureSetTextContent(unitsEl, statement.consumerNumber || '--');
            } else {
                securityModule.secureSetTextContent(unitsEl, statement.unitsConsumed || statement.units_consumed || 'N/A');
            }

            // Consumption info - only show for electricity bills
            const consumptionCard = document.querySelector('#bill-statement-screen .info-card:nth-of-type(2)');
            if (consumptionCard) {
                consumptionCard.style.display = isCreditCard ? 'none' : 'block';
            }
            
            if (!isCreditCard) {
                securityModule.secureSetTextContent(
                    document.getElementById('bill-avg-units'),
                    statement.monthlyAverageUnits || '--'
                );
                securityModule.secureSetTextContent(
                    document.getElementById('bill-highest-month'),
                    statement.highestConsumptionMonth || '--'
                );
                securityModule.secureSetTextContent(
                    document.getElementById('bill-highest-unit'),
                    statement.highestConsumptionUnit || '--'
                );
            }

            // Biller info - secure rendering
            securityModule.secureSetTextContent(
                document.getElementById('bill-biller-id'),
                statement.billerId || '--'
            );
            securityModule.secureSetTextContent(
                document.getElementById('bill-biller-name-info'),
                statement.billerName || '--'
            );
            
            // Tariff rate - only show for electricity bills
            const tariffRow = document.querySelector('#bill-biller-id').closest('.info-card').querySelector('.info-row:last-child');
            if (tariffRow) {
                tariffRow.style.display = isCreditCard ? 'none' : 'flex';
            }
            
            if (!isCreditCard) {
                securityModule.secureSetTextContent(
                    document.getElementById('bill-tariff'),
                    `₹ ${statement.tariffRate} per unit`
                );
            }

            // Transactions - only show for credit card statements
            const transactionsCard = document.getElementById('bill-transactions-card');
            const transactionsList = document.getElementById('bill-transactions-list');
            
            if (isCreditCard && statement.transactions && Array.isArray(statement.transactions) && statement.transactions.length > 0) {
                // Show transactions card
                if (transactionsCard) {
                    transactionsCard.style.display = 'block';
                }
                
                // Clear existing transactions
                if (transactionsList) {
                    transactionsList.innerHTML = '';
                    
                    // Render each transaction
                    statement.transactions.forEach((transaction, index) => {
                        const transactionItem = document.createElement('div');
                        transactionItem.className = 'payment-item';
                        
                        const itemContent = document.createElement('div');
                        itemContent.className = 'payment-item-content';
                        
                        const itemLeft = document.createElement('div');
                        itemLeft.className = 'payment-item-left';
                        
                        const transactionDateEl = document.createElement('div');
                        transactionDateEl.className = 'payment-date';
                        securityModule.secureSetTextContent(transactionDateEl, transaction.date || '--');
                        itemLeft.appendChild(transactionDateEl);
                        
                        const transactionDetails = document.createElement('div');
                        transactionDetails.className = 'payment-details';
                        
                        const descEl = document.createElement('div');
                        securityModule.secureSetTextContent(descEl, transaction.description || '--');
                        descEl.style.fontWeight = 'bold';
                        transactionDetails.appendChild(descEl);
                        
                        if (transaction.category) {
                            const categoryEl = document.createElement('div');
                            securityModule.secureSetTextContent(categoryEl, `Category: ${transaction.category}`);
                            categoryEl.style.fontSize = '0.85em';
                            categoryEl.style.color = '#666';
                            transactionDetails.appendChild(categoryEl);
                        }
                        
                        if (transaction.merchant) {
                            const merchantEl = document.createElement('div');
                            securityModule.secureSetTextContent(merchantEl, `Merchant: ${transaction.merchant}`);
                            merchantEl.style.fontSize = '0.85em';
                            merchantEl.style.color = '#666';
                            transactionDetails.appendChild(merchantEl);
                        }
                        
                        itemLeft.appendChild(transactionDetails);
                        itemContent.appendChild(itemLeft);
                        
                        const itemRight = document.createElement('div');
                        itemRight.className = 'payment-item-right';
                        const amountEl = document.createElement('div');
                        amountEl.className = 'payment-amount';
                        securityModule.secureSetTextContent(amountEl, `₹ ${transaction.amount || '0.00'}`);
                        itemRight.appendChild(amountEl);
                        itemContent.appendChild(itemRight);
                        
                        transactionItem.appendChild(itemContent);
                        
                        // Add divider between transactions (except last one)
                        if (index < statement.transactions.length - 1) {
                            const divider = document.createElement('div');
                            divider.className = 'payment-divider';
                            transactionItem.appendChild(divider);
                        }
                        
                        transactionsList.appendChild(transactionItem);
                    });
                }
            } else {
                // Hide transactions card for electricity bills or if no transactions
                if (transactionsCard) {
                    transactionsCard.style.display = 'none';
                }
            }

            // Consumer info - secure rendering
            // For credit cards, show "Card Number" label instead of "Consumer Number"
            const consumerNumberLabel = document.querySelector('#bill-consumer-number').previousElementSibling;
            if (consumerNumberLabel && consumerNumberLabel.classList.contains('info-label')) {
                consumerNumberLabel.textContent = isCreditCard ? 'Card Number' : 'Consumer Number';
            }
            
            securityModule.secureSetTextContent(
                document.getElementById('bill-consumer-name'),
                statement.consumerName || '--'
            );
            securityModule.secureSetTextContent(
                document.getElementById('bill-consumer-number'),
                statement.consumerNumber || '--'
            );

            console.log('All bill statement fields populated with secure rendering, switching screen...');
            this.showScreen('bill-statement');
        } catch (error) {
            console.error('Error rendering bill statement:', error);
            this.showError('Failed to render bill statement: ' + error.message);
        }
    }
    
    /**
     * Detect if statement is a credit card statement
     * @param {BillStatementModel} statement
     * @returns {boolean}
     */
    isCreditCardStatement(statement) {
        // Check if biller name contains "Bank" (e.g., "HDFC Bank")
        if (statement.billerName && statement.billerName.toLowerCase().includes('bank')) {
            return true;
        }
        
        // Check if units consumed is null/N/A (credit cards don't have units)
        const units = statement.unitsConsumed || statement.units_consumed;
        if (!units || units === 'N/A' || units === null) {
            // Additional check: if tariff rate is also N/A, it's likely a credit card
            if (statement.tariffRate === 'N/A' || !statement.tariffRate) {
                return true;
            }
        }
        
        // Check biller ID pattern (credit cards might have different IDs)
        if (statement.billerId && statement.billerId.toUpperCase().includes('BANK')) {
            return true;
        }
        
        return false;
    }

    /**
     * Render payment history data
     * Uses secure rendering to obfuscate sensitive data in DOM
     * @param {PaymentHistoryModel} history
     */
    renderPaymentHistory(history) {
        // Last payment card - secure rendering
        const latest = history.payments[0];
        if (latest) {
            securityModule.secureSetTextContent(
                document.getElementById('payment-last-amount'),
                `₹${latest.paymentAmount}`
            );
            securityModule.secureSetTextContent(
                document.getElementById('payment-last-date'),
                `Paid On: ${latest.paymentDate}`
            );
        }
        securityModule.secureSetTextContent(
            document.getElementById('payment-biller-name'),
            history.billerName || '--'
        );
        securityModule.secureSetTextContent(
            document.getElementById('payment-units'),
            history.unitsConsumed || 'N/A'
        );

        // Payment list - use secure rendering for each item
        const paymentListEl = document.getElementById('payment-list');
        paymentListEl.innerHTML = '';

        if (history.payments.length === 0) {
            paymentListEl.innerHTML = '<div class="info-row"><span class="info-value">No payments found.</span></div>';
        } else {
            history.payments.forEach((payment, index) => {
                const paymentItem = document.createElement('div');
                paymentItem.className = 'payment-item';
                
                // Create elements and use secure rendering
                const itemContent = document.createElement('div');
                itemContent.className = 'payment-item-content';
                
                const itemLeft = document.createElement('div');
                itemLeft.className = 'payment-item-left';
                
                const paymentDateEl = document.createElement('div');
                paymentDateEl.className = 'payment-date';
                securityModule.secureSetTextContent(paymentDateEl, payment.paymentDate || '--');
                itemLeft.appendChild(paymentDateEl);
                
                const paymentDetails = document.createElement('div');
                paymentDetails.className = 'payment-details';
                
                const amountLabel = document.createElement('div');
                amountLabel.textContent = 'Amount:';
                paymentDetails.appendChild(amountLabel);
                
                const txnEl = document.createElement('div');
                securityModule.secureSetTextContent(txnEl, `Txn: ${payment.transactionId || '--'}`);
                paymentDetails.appendChild(txnEl);
                
                const statusEl = document.createElement('div');
                securityModule.secureSetTextContent(statusEl, `Status: ${payment.status || '--'}`);
                paymentDetails.appendChild(statusEl);
                
                if (payment.paymentVia) {
                    const viaEl = document.createElement('div');
                    securityModule.secureSetTextContent(viaEl, `Paid via: ${payment.paymentVia}`);
                    paymentDetails.appendChild(viaEl);
                }
                
                itemLeft.appendChild(paymentDetails);
                itemContent.appendChild(itemLeft);
                
                const itemRight = document.createElement('div');
                itemRight.className = 'payment-item-right';
                const amountEl = document.createElement('div');
                amountEl.className = 'payment-amount';
                securityModule.secureSetTextContent(amountEl, `₹ ${payment.paymentAmount || '0.00'}`);
                itemRight.appendChild(amountEl);
                itemContent.appendChild(itemRight);
                
                paymentItem.appendChild(itemContent);
                
                if (index < history.payments.length - 1) {
                    const divider = document.createElement('div');
                    divider.className = 'payment-divider';
                    paymentItem.appendChild(divider);
                }
                
                paymentListEl.appendChild(paymentItem);
            });
        }

        // Consumer info - secure rendering
        securityModule.secureSetTextContent(
            document.getElementById('payment-consumer-name'),
            history.consumerName || '--'
        );
        securityModule.secureSetTextContent(
            document.getElementById('payment-consumer-number'),
            history.consumerNumber || '--'
        );

        // Biller info - secure rendering
        securityModule.secureSetTextContent(
            document.getElementById('payment-biller-id'),
            history.billerId || '--'
        );
        securityModule.secureSetTextContent(
            document.getElementById('payment-biller-name-info'),
            history.billerName || '--'
        );
        securityModule.secureSetTextContent(
            document.getElementById('payment-units-info'),
            history.unitsConsumed || 'N/A'
        );
        securityModule.secureSetTextContent(
            document.getElementById('payment-statement-id'),
            history.statementId || '--'
        );

        this.showScreen('payment-history');
    }
}

// Global UI controller instance
const uiController = new UIController();
