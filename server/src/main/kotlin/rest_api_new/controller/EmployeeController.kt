package com.example.api.controller

import com.example.api.repository.model.Employee
import com.example.api.service.EmployeeService
import com.rest_api.types.Tx
import com.rest_api.types.UTxO
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import java.math.BigInteger

/**
 * Controller for REST API endpoints
 */
@RestController
class EmployeeController(private val employeeService: EmployeeService) {

    //    List the entire ledger history since the Genesis UTxO ordered by the transaction timestamps. It should also support a limit on the number of transactions being returned.
    @GetMapping("/tx/{n}")
    fun getLedgerHistory(@PathVariable("n") n: Int): Int =
        employeeService.getAllTxs(n)

//    Send an amount of coins to the given address. The method should select any of the available UTxOs in- order to satisfy the request amount.

    //    List the entire transaction history for the given ad- dress ordered by the transaction timestamps. It should also support a limit on the number of transac- tions being returned.
    @GetMapping("/tx/{addr}/{n}")
    fun getAddrHistory(@PathVariable("addr") addr: String): Int =
        employeeService.getAddrTxs(addr)

    //    List all unspent transaction outputs for the given ad- dress
    @GetMapping("/utxo/{addr}")
    fun getAddrHistoryHead(@PathVariable("addr") addr: String, @PathVariable("n") n: Int): Int =
        employeeService.getTxsHead(addr, n)

    //    Submit a transaction / an atomic transaction list  => get the created tx id(s)
    @PostMapping("/tx")
    fun createTx(@RequestBody txList : List<Tx>): Int =
        employeeService.submitTx(txList)

    @PostMapping("/tr/{addr}/{amount}")
    fun createTr(@PathVariable("addr") addr: String, @PathVariable("amount") amount: UInt): Int =
        employeeService.submitTr(addr, amount)
}