# pnAutoMine

`pnAutoMine` - плагин для автоматических шахт на Paper-сервере.

Он позволяет создавать шахты из выделения WorldEdit, автоматически сбрасывать их по таймеру и показывать голограмму с текущим состоянием шахты.

## Возможности

- создание шахты по выделению WorldEdit
- автоматический сброс шахты по интервалу
- набор типов шахт с отдельным составом блоков
- автоматическая смена типа шахты по порядку
- голограмма над шахтой с названием, типом, блоками и временем до сброса
- команда просмотра списка и информации по шахтам
- поддержка PlaceholderAPI
- настраиваемый sidebar scoreboard через TAB и PlaceholderAPI
- поддержка FancyHolograms и DecentHolograms

## Команды

- `/pnautomine help` - показать помощь
- `/pnautomine create <id> <type>` - создать шахту из выделения WorldEdit
- `/pnautomine delete <id>` - удалить шахту
- `/pnautomine reset <id>` - сбросить шахту
- `/pnautomine resetall` - сбросить все шахты
- `/pnautomine list` - список шахт
- `/pnautomine info <id>` - информация о шахте
- `/pnautomine setspawn <id>` - установить точку телепорта
- `/pnautomine sethologram <id>` - установить позицию голограммы
- `/pnautomine reload` - перезагрузить плагин
- `/pnautomine types` - список типов шахт

Алиасы:

- `/mine`
- `/mines`
- `/am`

## Права

- `pnautomine.admin` - доступ к административным командам
- `pnautomine.use` - базовый доступ

## Требования

- Paper-сервер
- WorldEdit
- Java 17

Опционально:

- FancyHolograms
- DecentHolograms
- PlaceholderAPI
- TAB (для sidebar scoreboard)

## PlaceholderAPI и scoreboard TAB

pnAutoMine не заменяет scoreboard TAB: он предоставляет значения, а название,
строки, цвета и условия показа владелец сервера настраивает в
`plugins/TAB/config.yml`. После первого запуска пример создаётся в
`plugins/pnAutoMine/tab-scoreboard-example.yml`.

Placeholder текущей шахты определяется по позиции игрока:

- `%pnautomine_current_exists%` — находится ли игрок в шахте (`true`/`false`)
- `%pnautomine_current_id%` — ID текущей шахты
- `%pnautomine_current_name%` — отображаемое имя
- `%pnautomine_current_type%` — ID типа
- `%pnautomine_current_type_display%` — отображаемое имя типа
- `%pnautomine_current_next_type%` — ID типа после следующего сброса
- `%pnautomine_current_next_type_display%` — отображаемое имя следующего типа
- `%pnautomine_current_blocks_total%` — всего блоков
- `%pnautomine_current_blocks_remaining%` — осталось блоков
- `%pnautomine_current_blocks_mined%` — добыто блоков
- `%pnautomine_current_percentage%` — процент оставшихся блоков
- `%pnautomine_current_percentage_mined%` — процент добытых блоков
- `%pnautomine_current_reset_time%` — время до сброса
- `%pnautomine_current_reset_seconds%` — время до сброса в секундах
- `%pnautomine_current_reset_interval%` — интервал сброса в секундах
- `%pnautomine_current_world%` — мир шахты
- `%pnautomine_mine_count%` — число загруженных шахт

Следующая шахта по времени автоматического сброса:

- `%pnautomine_next_mine_id%`
- `%pnautomine_next_mine_name%`
- `%pnautomine_next_mine_type_display%`
- `%pnautomine_next_mine_reset_time%`
- `%pnautomine_next_mine_reset_seconds%`

Персональная добыча игрока в текущем цикле шахты:

- `%pnautomine_current_player_mined_total%` — всего сломано блоков
- `%pnautomine_current_player_mined_<group>%` — количество по группе из `mining-statistics.groups`
- `%pnautomine_current_player_mined_<material>%` — количество конкретного Bukkit Material
- `%pnautomine_current_player_earnings%` — сумма по `value-per-block`

Например: `%pnautomine_current_player_mined_iron%` и
`%pnautomine_current_player_mined_diamond_ore%`. Для общей добычи всех игроков
используются `%pnautomine_current_mined_<group>%` и
`%pnautomine_current_blocks_mined_<group>%`. Статистика очищается при сбросе
шахты и при перезапуске/reload плагина. Готовый scoreboard в стиле счётчика
добычи создаётся в `plugins/pnAutoMine/tab-mining-scoreboard-example.yml`.

Для конкретной шахты вместо `current` указывается её ID:
`%pnautomine_<mine_id>_<value>%`, например
`%pnautomine_spawn_blocks_remaining%`.

Требуются установленные PlaceholderAPI и TAB. Проверить результат можно
командой TAB: `/tab parse <игрок> %pnautomine_current_name%`.

## Настройка

Основные параметры находятся в `config.yml`:

- `defaults` - интервалы и поведение сброса
- `mine-types` - типы шахт и состав блоков
- `mine-type-progression` - порядок автоматической смены типов
- `mining-statistics` - группы материалов, цена за блок и формат зарплаты
- `hologram` - текст и позиция голограммы

## Установка

1. Положите `pnAutoMine.jar` в папку `plugins`.
2. Установите WorldEdit.
3. Запустите сервер один раз.
4. Настройте `config.yml`.
5. Создайте шахту через WorldEdit и команду `/pnautomine create`.


## pnLibrary

pnAutoMine uses the public pnLibrary API from Maven Central. The server must have the `pnLibrary` Bukkit/Paper runtime installed.

```gradle
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("io.github.pnfolder:pnlibrary-api:2.2.0-beta.2")
}
```

The plugin keeps its author metadata in `plugin.yml`, so pnLibrary can recognize the plugin and its developer information.
