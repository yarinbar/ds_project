package com.example.api.service

import com.example.api.exception.EmployeeNotFoundException
import com.example.api.repository.EmployeeRepository
import com.example.api.repository.model.Employee
import com.rest_api.types.Tx
import com.rest_api.types.UTxO
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

/**
 * Service for interactions with employee domain object
 */
@Service
class EmployeeService() {

    fun getAllTxs(n: Int): Int{
        println(n)
        return 1
    }

    fun getAddrTxs(addr: String): Int{
        return 1
    }

    fun getTxsHead(addr: String, n: Int): Int{
        return 1
    }

    fun submitTx(txList: List<Tx>): Int{
        return 1
    }

    fun submitTr(addr: String, amount: UInt): Int{
        return 1
    }
}