package com.drm.auth.service;

import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthGetBalance;
import org.web3j.utils.Convert;

import java.math.BigDecimal;
import java.math.BigInteger;

@Service
public class BlockchainService {

    private static final String HARDHAT_URL = "http://localhost:8545";
    private static final String GANACHE_URL = "http://localhost:7545";

    private Web3j web3j;
    private String activeUrl;

    public BlockchainService() {
        initWeb3j();
    }

    /**
     * Khởi tạo kết nối Web3j, thử kết nối tới Hardhat trước, nếu thất bại chuyển sang Ganache
     */
    private synchronized void initWeb3j() {
        try {
            // Thử Hardhat
            this.web3j = Web3j.build(new HttpService(HARDHAT_URL));
            EthBlockNumber blockNumber = this.web3j.ethBlockNumber().send();
            if (blockNumber.getBlockNumber() != null) {
                this.activeUrl = HARDHAT_URL;
                System.out.println("✅ Connected to Blockchain (Hardhat): " + HARDHAT_URL + " (Block #" + blockNumber.getBlockNumber() + ")");
                return;
            }
        } catch (Exception e) {
            System.out.println("⚠️ Hardhat not active on " + HARDHAT_URL + ", trying Ganache...");
        }

        try {
            // Thử Ganache
            this.web3j = Web3j.build(new HttpService(GANACHE_URL));
            EthBlockNumber blockNumber = this.web3j.ethBlockNumber().send();
            if (blockNumber.getBlockNumber() != null) {
                this.activeUrl = GANACHE_URL;
                System.out.println("✅ Connected to Blockchain (Ganache): " + GANACHE_URL + " (Block #" + blockNumber.getBlockNumber() + ")");
                return;
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to connect to local blockchain nodes: " + e.getMessage());
            this.web3j = null;
            this.activeUrl = null;
        }
    }

    /**
     * Kiểm tra và hồi phục kết nối Web3j nếu cần
     */
    private Web3j getWeb3j() {
        if (this.web3j == null) {
            initWeb3j();
        }
        return this.web3j;
    }

    public String getActiveUrl() {
        getWeb3j(); // Đảm bảo đã thử kết nối
        return activeUrl != null ? activeUrl : "Disconnected (Ganache/Hardhat offline)";
    }

    /**
     * Lấy số block mới nhất
     */
    public String getLatestBlockNumber() {
        Web3j client = getWeb3j();
        if (client == null) {
            return "N/A (Blockchain Offline)";
        }
        try {
            EthBlockNumber blockNumber = client.ethBlockNumber().send();
            return blockNumber.getBlockNumber().toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Lấy số dư ví bằng Ether
     */
    public BigDecimal getBalance(String walletAddress) {
        Web3j client = getWeb3j();
        if (client == null) {
            return BigDecimal.ZERO;
        }
        if (walletAddress == null || walletAddress.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            EthGetBalance balanceResponse = client.ethGetBalance(walletAddress, DefaultBlockParameterName.LATEST).send();
            BigInteger wei = balanceResponse.getBalance();
            return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
        } catch (Exception e) {
            System.err.println("Error fetching balance for wallet " + walletAddress + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
