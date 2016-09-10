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

    // Returns a negative integer, zero, or a positive integer is h1's date is greater than,
    // equal to, or less than h2's date. Sorts in DESC order!!!
    public int compare(HistoryDto h1, HistoryDto h2) {
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

    private Date getCompareDate(HistoryDto dto) {
        return dto.request.user.id.equals(userId) ? dto.request.postDate : dto.responses.get(0).responseTime;
    }
}