ALTER TABLE short_link_visit_event
    MODIFY visited_at DATETIME(3) NOT NULL,
    MODIFY created_at DATETIME(3) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(3);