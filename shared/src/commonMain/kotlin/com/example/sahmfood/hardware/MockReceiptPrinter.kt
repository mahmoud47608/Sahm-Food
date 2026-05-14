package com.example.sahmfood.hardware

import com.example.sahmfood.domain.Order
import com.example.sahmfood.domain.ReceiptPrinter
import io.github.aakira.napier.Napier
import kotlinx.coroutines.delay

class MockReceiptPrinter : ReceiptPrinter {
    override suspend fun print(order: Order): String {
        val text = renderAscii(order)
        Napier.i { "\n$text" }
        delay(400)
        return text
    }

    private fun renderAscii(o: Order): String = buildString {
        val w = 32
        val line = { ch: String -> appendLine(ch.repeat(w)) }
        val twoCol = { left: String, right: String ->
            val gap = (w - left.length - right.length).coerceAtLeast(1)
            appendLine(left + " ".repeat(gap) + right)
        }

        line("=")
        appendLine("Sahm Food".center(w))
        appendLine("Branch: ${o.branchId}".center(w))
        line("=")
        appendLine("Order:   ${o.orderNumber}")
        appendLine("Cashier: ${o.cashierId}")
        appendLine("Time:    ${o.paidAt}")
        line("-")
        o.items.forEach {
            twoCol("${it.quantity}x ${it.productName.take(20)}", it.lineTotal.formatted())
        }
        line("-")
        twoCol("Subtotal:", o.subtotal.formatted())
        twoCol("Tax:",      o.tax.formatted())
        if (o.discountBps > 0) twoCol("Discount (${o.discountBps / 100}%):", "-${o.discount.formatted()}")
        line("=")
        twoCol("TOTAL:", o.total.formatted())
        line("=")
        twoCol("CASH",   o.total.formatted())
        line("-")
        appendLine("شكراً لزيارتكم".center(w))
        line("=")
    }

    private fun String.center(w: Int): String {
        if (length >= w) return take(w)
        val pad = (w - length) / 2
        return " ".repeat(pad) + this + " ".repeat(w - length - pad)
    }
}
