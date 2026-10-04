# VaultZero — Data Model

> Room entities, SQLCipher schema, DAO contracts, and KDBX compatibility rationale. Builder/Finisher reference.

## 1. Design Decisions

### 1.1 SQLCipher over plain SQLite
- SQLCipher provides transparent AES-256 encryption at the page level.
- The entire DB file is encrypted with the derived master key.
- Room abstracts SQLCipher via `SupportSQLiteOpenHelper`.

### 1.2 KDBX Compatibility: Import-Only
- KDBX (KeePass) is the de-facto password manager format.
- VaultZero does **not** use KDBX as its native format.
- Rationale:
  - KDBX is complex (inner/outer encryption, transform rounds, XML payload).
  - Full compatibility requires handling KDBX 3.x and 4.x, attachments, custom data.
  - VaultZero’s native format is simpler, versioned, and auditable.
- **Decision:** Import KDBX → convert to native vault. Export native `.vzb` only.

## 2. Room Entities

### 2.1 VaultMeta
```kotlin
@Entity(tableName = "vault_meta")
data class VaultMeta(
    @PrimaryKey
    val id: Int = 1,  // singleton

    val kdfType: Int,        // 1=Argon2id, 2=PBKDF2
    val kdfSalt: ByteArray,  // 16 bytes
    val kdfParamsJson: String,  // {"memory":65536,"iterations":3,"parallelism":4}

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: Int = 1
) {
    override fun equals(other: Any?): Boolean { /* ByteArray content equality */ }
    override fun hashCode(): Int { /* ByteArray content hash */ }
}
```

### 2.2 Entry
```kotlin
@Entity(tableName = "entries")
data class Entry(
    @PrimaryKey
    val uuid: UUID = UUID.randomUUID(),

    val title: String,
    val username: String? = null,

    // Encrypted BLOBs — AES-256-GCM with per-entry key
    val passwordCiphertext: ByteArray,
    val passwordNonce: ByteArray,  // 12 bytes

    val url: String? = null,

    val notesCiphertext: ByteArray? = null,
    val notesNonce: ByteArray? = null,

    val tags: String? = null,  // comma-separated, e.g. "work,finance"

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val favorite: Boolean = false
) {
    override fun equals(other: Any?): Boolean { /* ByteArray content equality */ }
    override fun hashCode(): Int { /* ByteArray content hash */ }
}
```

### 2.3 Folder
```kotlin
@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey
    val uuid: UUID = UUID.randomUUID(),
    val name: String,
    val parentUuid: UUID? = null,  // null = root
    val createdAt: Long = System.currentTimeMillis()
)
```

### 2.4 EntryFolder (Join)
```kotlin
@Entity(
    tableName = "entry_folder",
    primaryKeys = ["entryUuid", "folderUuid"],
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["uuid"],
            childColumns = ["entryUuid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["uuid"],
            childColumns = ["folderUuid"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EntryFolder(
    val entryUuid: UUID,
    val folderUuid: UUID
)
```

### 2.5 AuditLog
```kotlin
@Entity(tableName = "audit_log")
data class AuditLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val action: String,           // "CREATE", "UPDATE", "DELETE", "EXPORT", "UNLOCK", "LOCK"
    val entityUuid: UUID? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val detailJson: String? = null  // no sensitive data
)
```

## 3. DAO Contracts

### 3.1 EntryDao
```kotlin
@Dao
interface EntryDao {
    @Query("SELECT * FROM entries ORDER BY favorite DESC, title ASC")
    fun observeAll(): Flow<List<Entry>>

    @Query("SELECT * FROM entries WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: UUID): Entry?

    @Query("SELECT * FROM entries WHERE tags LIKE '%' || :tag || '%' ORDER BY title")
    suspend fun searchByTag(tag: String): List<Entry>

    @Query("SELECT * FROM entries WHERE title LIKE '%' || :query || '%' OR username LIKE '%' || :query || '%'")
    suspend fun search(query: String): List<Entry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: Entry): Long

    @Update
    suspend fun update(entry: Entry)

    @Delete
    suspend fun delete(entry: Entry)

    @Query("SELECT COUNT(*) FROM entries")
    suspend fun count(): Int
}
```

### 3.2 FolderDao
```kotlin
@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY name")
    fun observeAll(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parent_uuid IS NULL ORDER BY name")
    suspend fun getRootFolders(): List<Folder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)
}
```

### 3.3 EntryFolderDao
```kotlin
@Dao
interface EntryFolderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun associate(entryUuid: UUID, folderUuid: UUID)

    @Query("DELETE FROM entry_folder WHERE entry_uuid = :entryUuid AND folder_uuid = :folderUuid")
    suspend fun dissociate(entryUuid: UUID, folderUuid: UUID)

    @Query("SELECT f.* FROM folders f INNER JOIN entry_folder ef ON f.uuid = ef.folder_uuid WHERE ef.entry_uuid = :entryUuid")
    suspend fun getFoldersForEntry(entryUuid: UUID): List<Folder>

    @Query("SELECT e.* FROM entries e INNER JOIN entry_folder ef ON e.uuid = ef.entry_uuid WHERE ef.folder_uuid = :folderUuid ORDER BY e.title")
    suspend fun getEntriesInFolder(folderUuid: UUID): List<Entry>
}
```

### 3.4 AuditLogDao
```kotlin
@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(log: AuditLog)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 100): List<AuditLog>

    @Query("DELETE FROM audit_log WHERE timestamp < :cutoff")
    suspend fun pruneOlderThan(cutoff: Long)
}
```

### 3.5 VaultMetaDao
```kotlin
@Dao
interface VaultMetaDao {
    @Query("SELECT * FROM vault_meta WHERE id = 1")
    suspend fun get(): VaultMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: VaultMeta)
}
```

## 4. SQLCipher + Room Integration

### 4.1 Custom Open Helper
```kotlin
class SqlCipherHelper(
    context: Context,
    private val passphrase: ByteArray
) : SupportSQLiteOpenHelper.Callback(1) {

    override fun onCreate(db: SupportSQLiteDatabase) {
        // Room generates CREATE TABLE statements automatically
    }

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrations handled by Room
    }

    fun createFactory(): SupportSQLiteOpenHelper.Factory {
        return SupportFactory(passphrase)  // from sqlcipher:android-database-sqlcipher
    }
}
```

### 4.2 Database Builder
```kotlin
fun createDatabase(context: Context, passphrase: ByteArray): VaultDatabase {
    val factory = SqlCipherHelper(context, passphrase).createFactory()
    return Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
        .openHelperFactory(factory)
        .build()
}
```

### 4.3 Database Access Pattern
- `VaultRepository` holds `VaultDatabase` instance.
- `VaultDatabase` is opened after unlock, closed on lock.
- The passphrase is the derived master key (32 bytes from Argon2id/PBKDF2).
- SQLCipher transparently encrypts/decrypts pages using this passphrase.

## 5. Encrypted Field Handling

### 5.1 Repository Layer Decryption
```kotlin
class EntryRepository(
    private val entryDao: EntryDao,
    private val cryptoEngine: CryptoEngine,
    private val session: VaultSession
) {
    suspend fun getDecryptedEntry(uuid: UUID): DecryptedEntry? {
        val entry = entryDao.getByUuid(uuid) ?: return null
        val entryKey = cryptoEngine.deriveEntryKey(session.masterKey, entry.uuid)
        return DecryptedEntry(
            uuid = entry.uuid,
            title = entry.title,
            username = entry.username,
            password = cryptoEngine.decrypt(entry.passwordCiphertext, entryKey, entry.passwordNonce),
            url = entry.url,
            notes = entry.notesCiphertext?.let { cryptoEngine.decrypt(it, entryKey, entry.notesNonce!!) },
            tags = entry.tags?.split(",") ?: emptyList(),
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
            favorite = entry.favorite
        )
    }
}
```

### 5.2 TypeConverters (Room)
```kotlin
class Converters {
    @TypeConverter
    fun fromByteArray(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    @TypeConverter
    fun toByteArray(base64: String): ByteArray = Base64.decode(base64, Base64.NO_WRAP)

    @TypeConverter
    fun fromUuid(uuid: UUID): String = uuid.toString()

    @TypeConverter
    fun toUuid(str: String): UUID = UUID.fromString(str)
}
```

## 6. Migration Strategy

### 6.1 Version 1 → 2 (Future)
- Add `totp_secret` and `totp_digits` to `Entry` (MFA support).
- Migration: Room auto-generated `Migration(1, 2)`.
- Schema version bump in `VaultMeta.version`.

### 6.2 Export/Import as Universal Migration
- If a migration is destructive (re-encryption needed), the builder may:
  1. Export current vault to `.vzb`.
  2. Create new DB with new schema.
  3. Re-import and re-encrypt each entry.
- This is slow but safe and offline-only.

## 7. KDBX Import Mapping

### 7.1 KDBX → VaultZero Field Mapping
| KDBX Field | VaultZero Field | Notes |
|------------|-----------------|-------|
| `Title` | `Entry.title` | plain |
| `UserName` | `Entry.username` | plain |
| `Password` | `Entry.passwordCiphertext` | encrypted |
| `URL` | `Entry.url` | plain |
| `Notes` | `Entry.notesCiphertext` | encrypted |
| `Tags` | `Entry.tags` | comma-separated |
| `Group` | `Folder` + `EntryFolder` | recursive groups → flat folders |
| `IconID` | — | ignored for MVP |
| `Binary` | — | ignored for MVP (no attachments) |

### 7.2 Import Flow
1. Parse KDBX outer header → extract KDF params (Argon2id or AES-KDF).
2. Derive composite key from password + optional keyfile.
3. Decrypt inner XML payload.
4. Iterate `<Entry>` nodes, map to VaultZero entities.
5. Encrypt each entry with per-entry key.
6. Insert into native vault DB.
7. Wipe intermediate plaintext from memory.

## 8. Entity Relationship Diagram

```
┌──────────────┐       ┌─────────────────┐       ┌─────────────┐
│  vault_meta  │1      │     entries     │*      │   folders   │
│   (singleton)│       │                 │       │             │
└──────────────┘       │ uuid (PK)       │       │ uuid (PK)   │
                       │ title           │       │ name        │
                       │ username        │       │ parent_uuid │
                       │ passwordCipher  │       │ created_at  │
                       │ passwordNonce   │       └─────────────┘
                       │ url             │              *
                       │ notesCipher     │              │
                       │ notesNonce      │              │
                       │ tags            │       ┌─────────────┐
                       │ favorite        │       │ entry_folder│
                       │ created_at      │       │ entry_uuid  │
                       │ updated_at      │       │ folder_uuid │
                       └─────────────────┘       └─────────────┘
                                │
                                │ 1
                       ┌────────┴────────┐
                       │   audit_log     │
                       │ id (PK)         │
                       │ action          │
                       │ entity_uuid     │
                       │ timestamp       │
                       │ detail_json     │
                       └─────────────────┘
```

## 9. References
- ARCHITECTURE.md — threat model, encryption scheme, biometric flow.
- CRYPTO_SPEC.md — exact algorithms, constants, test vectors.
- API_CONTRACT.md — Kotlin interfaces for Builder/Finisher.

---
*Version: 1.0 | VaultZero Codename*
