package com.tnt.assignment.model;

import lombok.Data;

@Data
public class Mutable<T> {
    private T value;
}
