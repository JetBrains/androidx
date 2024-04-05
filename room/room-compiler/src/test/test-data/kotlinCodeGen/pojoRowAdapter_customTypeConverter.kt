import androidx.room.EntityInsertionAdapter
import androidx.room.RoomDatabase
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performBlocking
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.db.SupportSQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION"])
public class MyDao_Impl(
  __db: RoomDatabase,
) : MyDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfMyEntity: EntityInsertionAdapter<MyEntity>

  private val __fooConverter: FooConverter = FooConverter()
  init {
    this.__db = __db
    this.__insertAdapterOfMyEntity = object : EntityInsertionAdapter<MyEntity>(__db) {
      protected override fun createQuery(): String =
          "INSERT OR ABORT INTO `MyEntity` (`pk`,`foo`) VALUES (?,?)"

      protected override fun bind(statement: SupportSQLiteStatement, entity: MyEntity) {
        statement.bindLong(1, entity.pk.toLong())
        val _tmp: String = __fooConverter.toString(entity.foo)
        statement.bindString(2, _tmp)
      }
    }
  }

  public override fun addEntity(item: MyEntity) {
    __db.assertNotSuspendingTransaction()
    __db.beginTransaction()
    try {
      __insertAdapterOfMyEntity.insert(item)
      __db.setTransactionSuccessful()
    } finally {
      __db.endTransaction()
    }
  }

  public override fun getEntity(): MyEntity {
    val _sql: String = "SELECT * FROM MyEntity"
    return performBlocking(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _cursorIndexOfPk: Int = getColumnIndexOrThrow(_stmt, "pk")
        val _cursorIndexOfFoo: Int = getColumnIndexOrThrow(_stmt, "foo")
        val _result: MyEntity
        if (_stmt.step()) {
          val _tmpPk: Int
          _tmpPk = _stmt.getLong(_cursorIndexOfPk).toInt()
          val _tmpFoo: Foo
          val _tmp: String
          _tmp = _stmt.getText(_cursorIndexOfFoo)
          _tmpFoo = __fooConverter.fromString(_tmp)
          _result = MyEntity(_tmpPk,_tmpFoo)
        } else {
          error("The query result was empty, but expected a single row to return a NON-NULL object of type <MyEntity>.")
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
