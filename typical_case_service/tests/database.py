from app.core.database import (
    close_database_engine,
    create_database_engine,
    verify_database_connection,
)


def main() -> None:
    engine = create_database_engine()

    try:
        verify_database_connection(engine)
        print("数据库连接成功")

    finally:
        close_database_engine(engine)


if __name__ == "__main__":
    main()