package com.expensetracker.model;

/**
 * Fixed list of expense categories (the shared contract).
 * Stored in the DB as the enum name (e.g. "Food") via EnumType.STRING.
 */
public enum Category {
    Food,
    Transport,
    Shopping,
    Utilities,
    Rent,
    Subscriptions,
    Health,
    Entertainment,
    Education,
    Travel,
    Other
}
