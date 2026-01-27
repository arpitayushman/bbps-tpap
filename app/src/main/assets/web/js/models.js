/**
 * Data Models - Matching Kotlin serializable models
 */

class BillStatementModel {
    constructor(data) {
        this.statementId = data.statementId || '';
        this.billDate = data.billDate || '';
        this.dueDate = data.dueDate || '';
        this.amountDue = data.amountDue || '';
        this.consumerName = data.consumerName || '';
        this.consumerNumber = data.consumerNumber || '';
        this.mobileNumber = data.mobileNumber || '';
        this.billerId = data.billerId || '';
        this.billerName = data.billerName || '';
        this.monthlyAverageUnits = data.monthlyAverageUnits || '';
        this.highestConsumptionMonth = data.highestConsumptionMonth || '';
        this.highestConsumptionUnit = data.highestConsumptionUnit || '';
        this.tariffRate = data.tariffRate || '';
        this.unitsConsumed = data.unitsConsumed || data.units_consumed || null;
    }
}

class PaymentHistoryModel {
    constructor(data) {
        this.statementId = data.statementId || '';
        this.consumerName = data.consumerName || '';
        this.consumerNumber = data.consumerNumber || '';
        this.mobileNumber = data.mobileNumber || '';
        this.billerId = data.billerId || '';
        this.billerName = data.billerName || '';
        this.unitsConsumed = data.unitsConsumed || data.units_consumed || null;
        this.payments = (data.payments || []).map(p => new PaymentItem(p));
    }
}

class PaymentItem {
    constructor(data) {
        this.paymentDate = data.paymentDate || '';
        this.paymentAmount = data.paymentAmount || '';
        this.transactionId = data.transactionId || '';
        this.status = data.status || '';
        this.mode = data.mode || '';
        this.paymentVia = data.paymentVia || null;
    }
}
