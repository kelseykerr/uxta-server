package com.impulsecontrol.lend.model;

import com.impulsecontrol.lend.dto.HistoryDto;

import java.util.Comparator;
import java.util.Date;

/**
 * Created by kerrk on 9/10/16.
 */
public class HistoryComparator implements Comparator<HistoryDto> {

    private String userId;

    public HistoryComparator(String userId) {
        this.userId = userId;
    }

    // Returns a negative integer, zero, or a positive integer if, sorting by:
    // h1's date is greater than, equal to, or less than h2's date. Sorts in DESC order!!!
    /**
     * Put all open transactions at the top, sorted by date
     * Then put all open requests & open offers, sorted by date
     * then sort the remain by date
     * @param h1
     * @param h2
     * @return
     */
    public int compare(HistoryDto h1, HistoryDto h2) {
        // is the transaction open? if so, put in at the beginning
        boolean h1Transaction = h1.request != null && h1.request.status != null && h1.request.status.equalsIgnoreCase(Request.Status.TRANSACTION_PENDING.toString());
        boolean h2Transaction = h2.request != null && h2.request.status != null && h2.request.status.equalsIgnoreCase(Request.Status.TRANSACTION_PENDING.toString());
        boolean h1IsOpen = isOpen(h1);
        boolean h2IsOpen = isOpen(h2);
        if (h1Transaction && !h2Transaction) {
            return -1;
        } else if (h2Transaction && !h1Transaction) {
            return 1;
        } else if (h1IsOpen && !h2IsOpen) {
            return -1;
        }  else if (h2IsOpen && !h1IsOpen) {
            return 1;
        } else {
            Date h1Date = getCompareDate(h1);
            Date h2Date = getCompareDate(h2);
            int order =  h1Date.compareTo(h2Date);// in asc
            // flip values to make desc
            if (order < 0) {
                order = 1;
            } else if (order > 0) {
                order = -1;
            }
            return order;
        }
    }

    private Date getCompareDate(HistoryDto dto) {
        return dto.request.user.id.equals(userId) ? dto.request.postDate : dto.responses.get(0).responseTime;
    }

    private boolean isOpen(HistoryDto dto) {
        // the user is the requester
        if (dto.request.user.id.equals(userId)) {
            return dto.request.status.toLowerCase().equals(Request.Status.OPEN.toString().toLowerCase());
        } else { // user is buyer..check if the offer is pending, otherwise it's closed and will go at the bottom
                // or it's accepted and it's a transaction and will go at the top
            return dto.responses.get(0).responseStatus.toLowerCase().equals(Response.Status.PENDING.toString().toLowerCase());
        }
    }
}