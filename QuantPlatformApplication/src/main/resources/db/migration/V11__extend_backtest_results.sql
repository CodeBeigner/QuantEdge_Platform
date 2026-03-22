ALTER TABLE backtest_results
    ADD COLUMN IF NOT EXISTS is_walk_forward_validated BOOLEAN DEFAULT false,
    ADD COLUMN IF NOT EXISTS walk_forward_windows INTEGER,
    ADD COLUMN IF NOT EXISTS walk_forward_mean_sharpe NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS walk_forward_std_sharpe NUMERIC(10,4),
    ADD COLUMN IF NOT EXISTS transaction_costs NUMERIC(20,8);
