# shadcn-vue 컴포넌트별 상세 Cursor Rules

이 문서는 [shadcn-vue](https://www.shadcn-vue.com/docs/introduction.html) 컴포넌트를 사용할 때 Cursor AI가 따라야 할 상세한 규칙들을 컴포넌트별로 정리한 것입니다.

## 📋 목차

1. [기본 설정 및 공통 규칙](#기본-설정-및-공통-규칙)
2. [UI 컴포넌트별 규칙](#ui-컴포넌트별-규칙)
3. [폼 컴포넌트별 규칙](#폼-컴포넌트별-규칙)
4. [레이아웃 컴포넌트별 규칙](#레이아웃-컴포넌트별-규칙)
5. [네비게이션 컴포넌트별 규칙](#네비게이션-컴포넌트별-규칙)
6. [피드백 컴포넌트별 규칙](#피드백-컴포넌트별-규칙)
7. [데이터 표시 컴포넌트별 규칙](#데이터-표시-컴포넌트별-규칙)

---

## 기본 설정 및 공통 규칙

### 🎯 핵심 원칙
- **Vue 3 Composition API** 사용 필수
- **TypeScript** 지원 권장
- **Tailwind CSS**를 스타일링 프레임워크로 사용
- **재사용 가능한 컴포넌트** 설계
- **접근성(a11y)** 고려 필수

### 📝 코드 스타일 가이드
```typescript
// ✅ 좋은 예시
interface ButtonProps {
  variant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link'
  size?: 'default' | 'sm' | 'lg' | 'icon'
  disabled?: boolean
  loading?: boolean
}

// ❌ 나쁜 예시
interface ButtonProps {
  variant: any
  size: any
  disabled: any
}
```

---

## UI 컴포넌트별 규칙

### 🔘 Button 컴포넌트

```vue
<template>
  <Button 
    variant="default" 
    size="default" 
    :disabled="isLoading"
    :loading="isLoading"
    @click="handleClick"
    class="cursor-pointer"
  >
    <Icon v-if="icon" :name="icon" class="mr-2" />
    {{ text }}
  </Button>
</template>

<script setup lang="ts">
interface Props {
  variant?: 'default' | 'destructive' | 'outline' | 'secondary' | 'ghost' | 'link'
  size?: 'default' | 'sm' | 'lg' | 'icon'
  disabled?: boolean
  loading?: boolean
  icon?: string
  text: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default',
  size: 'default',
  disabled: false,
  loading: false
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const handleClick = (event: MouseEvent) => {
  if (!props.disabled && !props.loading) {
    emit('click', event)
  }
}
</script>
```

**규칙:**
- `variant`와 `size`는 명시적 타입 정의
- `disabled` 상태에서 클릭 이벤트 차단
- `loading` 상태 시 스피너 표시
- `cursor: pointer` 자동 적용
- 접근성: `aria-disabled`, `role="button"` 고려

### 🃏 Card 컴포넌트

```vue
<template>
  <Card 
    :class="[
      'transition-all duration-200',
      clickable ? 'cursor-pointer hover:shadow-lg' : '',
      className
    ]"
    @click="handleCardClick"
  >
    <CardHeader v-if="$slots.header || title">
      <CardTitle v-if="title">{{ title }}</CardTitle>
      <CardDescription v-if="description">{{ description }}</CardDescription>
      <slot name="header" />
    </CardHeader>
    
    <CardContent>
      <slot />
    </CardContent>
    
    <CardFooter v-if="$slots.footer">
      <slot name="footer" />
    </CardFooter>
  </Card>
</template>

<script setup lang="ts">
interface Props {
  title?: string
  description?: string
  clickable?: boolean
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  clickable: false
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const handleCardClick = (event: MouseEvent) => {
  if (props.clickable) {
    emit('click', event)
  }
}
</script>
```

**규칙:**
- `CardHeader`, `CardContent`, `CardFooter`는 선택적 사용
- 클릭 가능한 카드는 `cursor: pointer` 적용
- hover 효과는 부드러운 전환으로 구현
- 슬롯을 통한 유연한 내용 구성

### 🎨 Badge 컴포넌트

```vue
<template>
  <Badge 
    :variant="variant"
    :class="[
      'inline-flex items-center gap-1',
      className
    ]"
  >
    <Icon v-if="icon" :name="icon" class="w-3 h-3" />
    {{ text }}
  </Badge>
</template>

<script setup lang="ts">
interface Props {
  variant?: 'default' | 'secondary' | 'destructive' | 'outline'
  text: string
  icon?: string
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default'
})
</script>
```

**규칙:**
- `variant`로 의미에 맞는 색상 사용
- 텍스트는 짧고 명확하게
- 아이콘과 함께 사용 시 적절한 간격
- 접근성: `role="status"` 고려

---

## 폼 컴포넌트별 규칙

### 📝 Input 컴포넌트

```vue
<template>
  <div class="space-y-2">
    <Label v-if="label" :for="inputId" class="text-sm font-medium">
      {{ label }}
      <span v-if="required" class="text-destructive ml-1">*</span>
    </Label>
    
    <div class="relative">
      <Input
        :id="inputId"
        v-model="modelValue"
        :type="type"
        :placeholder="placeholder"
        :disabled="disabled"
        :class="[
          'transition-colors',
          error ? 'border-destructive focus-visible:ring-destructive' : '',
          className
        ]"
        :aria-invalid="!!error"
        :aria-describedby="error ? `${inputId}-error` : undefined"
        @blur="handleBlur"
        @focus="handleFocus"
      />
      
      <Icon 
        v-if="icon" 
        :name="icon" 
        class="absolute right-3 top-1/2 transform -translate-y-1/2 text-muted-foreground"
      />
    </div>
    
    <p 
      v-if="error" 
      :id="`${inputId}-error`"
      class="text-sm text-destructive"
    >
      {{ error }}
    </p>
    
    <p v-if="description && !error" class="text-sm text-muted-foreground">
      {{ description }}
    </p>
  </div>
</template>

<script setup lang="ts">
interface Props {
  modelValue: string
  type?: 'text' | 'email' | 'password' | 'number' | 'tel' | 'url'
  label?: string
  placeholder?: string
  description?: string
  error?: string
  required?: boolean
  disabled?: boolean
  icon?: string
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'text',
  required: false,
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  blur: [event: FocusEvent]
  focus: [event: FocusEvent]
}>()

const inputId = `input-${Math.random().toString(36).substr(2, 9)}`

const handleBlur = (event: FocusEvent) => {
  emit('blur', event)
}

const handleFocus = (event: FocusEvent) => {
  emit('focus', event)
}
</script>
```

**규칙:**
- `v-model` 양방향 바인딩 필수
- `label`과 `input` 연결 (`for` 속성)
- `error` 상태 시 시각적 피드백
- 접근성: `aria-invalid`, `aria-describedby` 고려
- `required` 표시는 명확하게

### 📋 Select 컴포넌트

```vue
<template>
  <div class="space-y-2">
    <Label v-if="label" :for="selectId" class="text-sm font-medium">
      {{ label }}
      <span v-if="required" class="text-destructive ml-1">*</span>
    </Label>
    
    <Select v-model="modelValue" :disabled="disabled">
      <SelectTrigger 
        :id="selectId"
        :class="[
          'cursor-pointer',
          error ? 'border-destructive focus:ring-destructive' : '',
          className
        ]"
        :aria-invalid="!!error"
        :aria-describedby="error ? `${selectId}-error` : undefined"
      >
        <SelectValue :placeholder="placeholder" />
        <Icon name="chevron-down" class="h-4 w-4 opacity-50" />
      </SelectTrigger>
      
      <SelectContent>
        <SelectItem 
          v-for="option in options" 
          :key="option.value"
          :value="option.value"
          :disabled="option.disabled"
        >
          <div class="flex items-center gap-2">
            <Icon v-if="option.icon" :name="option.icon" class="w-4 h-4" />
            {{ option.label }}
          </div>
        </SelectItem>
      </SelectContent>
    </Select>
    
    <p 
      v-if="error" 
      :id="`${selectId}-error`"
      class="text-sm text-destructive"
    >
      {{ error }}
    </p>
    
    <p v-if="description && !error" class="text-sm text-muted-foreground">
      {{ description }}
    </p>
  </div>
</template>

<script setup lang="ts">
interface Option {
  value: string
  label: string
  icon?: string
  disabled?: boolean
}

interface Props {
  modelValue: string
  options: Option[]
  label?: string
  placeholder?: string
  description?: string
  error?: string
  required?: boolean
  disabled?: boolean
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  required: false,
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const selectId = `select-${Math.random().toString(36).substr(2, 9)}`
</script>
```

**규칙:**
- `options` 배열로 선택지 관리
- `SelectTrigger`에 `cursor: pointer` 적용
- 키보드 네비게이션 자동 지원
- 접근성: `aria-expanded`, `role="combobox"` 자동 적용

### ✅ Checkbox 컴포넌트

```vue
<template>
  <div class="flex items-center space-x-2">
    <Checkbox
      :id="checkboxId"
      v-model:checked="modelValue"
      :disabled="disabled"
      :class="[
        'cursor-pointer',
        error ? 'border-destructive' : '',
        className
      ]"
      :aria-invalid="!!error"
      :aria-describedby="error ? `${checkboxId}-error` : undefined"
    />
    
    <div class="grid gap-1.5 leading-none">
      <Label 
        :for="checkboxId" 
        class="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 cursor-pointer"
      >
        {{ label }}
        <span v-if="required" class="text-destructive ml-1">*</span>
      </Label>
      
      <p v-if="description" class="text-xs text-muted-foreground">
        {{ description }}
      </p>
      
      <p 
        v-if="error" 
        :id="`${checkboxId}-error`"
        class="text-xs text-destructive"
      >
        {{ error }}
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  modelValue: boolean
  label: string
  description?: string
  error?: string
  required?: boolean
  disabled?: boolean
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  required: false,
  disabled: false
})

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const checkboxId = `checkbox-${Math.random().toString(36).substr(2, 9)}`
</script>
```

**규칙:**
- `v-model:checked`로 boolean 값 관리
- 라벨과 체크박스 연결
- `cursor: pointer` 적용
- 접근성: `aria-checked` 자동 관리

---

## 레이아웃 컴포넌트별 규칙

### 📦 Dialog 컴포넌트

```vue
<template>
  <Dialog v-model:open="isOpen">
    <DialogTrigger asChild>
      <Button variant="outline" class="cursor-pointer">
        {{ triggerText }}
      </Button>
    </DialogTrigger>
    
    <DialogContent 
      :class="[
        'sm:max-w-md',
        className
      ]"
    >
      <DialogHeader>
        <DialogTitle>{{ title }}</DialogTitle>
        <DialogDescription v-if="description">
          {{ description }}
        </DialogDescription>
      </DialogHeader>
      
      <div class="py-4">
        <slot />
      </div>
      
      <DialogFooter v-if="$slots.footer || showDefaultFooter">
        <slot name="footer">
          <Button variant="outline" @click="closeDialog">
            취소
          </Button>
          <Button @click="handleConfirm">
            확인
          </Button>
        </slot>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts">
interface Props {
  title: string
  description?: string
  triggerText: string
  showDefaultFooter?: boolean
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  showDefaultFooter: true
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const isOpen = ref(false)

const closeDialog = () => {
  isOpen.value = false
  emit('cancel')
}

const handleConfirm = () => {
  emit('confirm')
  closeDialog()
}
</script>
```

**규칙:**
- `v-model:open`으로 열림/닫힘 상태 관리
- `DialogTrigger`는 `asChild` prop 사용
- ESC 키와 배경 클릭으로 닫기 자동 제공
- 접근성: `aria-modal`, `role="dialog"` 자동 적용
- 포커스 트랩 자동 적용

### 📋 Sheet 컴포넌트

```vue
<template>
  <Sheet v-model:open="isOpen">
    <SheetTrigger asChild>
      <Button variant="outline" class="cursor-pointer">
        {{ triggerText }}
      </Button>
    </SheetTrigger>
    
    <SheetContent :side="side" :class="className">
      <SheetHeader>
        <SheetTitle>{{ title }}</SheetTitle>
        <SheetDescription v-if="description">
          {{ description }}
        </SheetDescription>
      </SheetHeader>
      
      <div class="py-4">
        <slot />
      </div>
      
      <SheetFooter v-if="$slots.footer">
        <slot name="footer" />
      </SheetFooter>
    </SheetContent>
  </Sheet>
</template>

<script setup lang="ts">
interface Props {
  title: string
  description?: string
  triggerText: string
  side?: 'top' | 'right' | 'bottom' | 'left'
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  side: 'right'
})

const isOpen = ref(false)
</script>
```

**규칙:**
- `side` prop으로 슬라이드 방향 설정
- 모바일 친화적 디자인
- 스와이프 제스처 지원
- 접근성: `aria-hidden` 자동 관리

---

## 네비게이션 컴포넌트별 규칙

### 🧭 Navigation Menu 컴포넌트

```vue
<template>
  <NavigationMenu>
    <NavigationMenuList>
      <NavigationMenuItem 
        v-for="item in items" 
        :key="item.value"
      >
        <NavigationMenuTrigger 
          v-if="item.children"
          class="cursor-pointer"
        >
          {{ item.label }}
        </NavigationMenuTrigger>
        
        <NavigationMenuContent v-if="item.children">
          <ul class="grid gap-3 p-6 md:w-[400px] lg:w-[500px] lg:grid-cols-[.75fr_1fr]">
            <li 
              v-for="child in item.children" 
              :key="child.value"
              class="row-span-3"
            >
              <NavigationMenuLink asChild>
                <a
                  :href="child.href"
                  class="flex h-full w-full select-none flex-col justify-end rounded-md bg-gradient-to-b from-muted/50 to-muted p-6 no-underline outline-none focus:shadow-md cursor-pointer"
                >
                  <Icon :name="child.icon" class="h-6 w-6" />
                  <div class="mb-2 mt-4 text-lg font-medium">
                    {{ child.label }}
                  </div>
                  <p class="text-sm leading-tight text-muted-foreground">
                    {{ child.description }}
                  </p>
                </a>
              </NavigationMenuLink>
            </li>
          </ul>
        </NavigationMenuContent>
        
        <NavigationMenuLink v-else asChild>
          <a
            :href="item.href"
            class="group inline-flex h-10 w-max items-center justify-center rounded-md bg-background px-4 py-2 text-sm font-medium transition-colors hover:bg-accent hover:text-accent-foreground focus:bg-accent focus:text-accent-foreground focus:outline-none disabled:pointer-events-none disabled:opacity-50 data-[active]:bg-accent/50 data-[state=open]:bg-accent/50 cursor-pointer"
          >
            {{ item.label }}
          </a>
        </NavigationMenuLink>
      </NavigationMenuItem>
    </NavigationMenuList>
  </NavigationMenu>
</template>

<script setup lang="ts">
interface NavigationItem {
  value: string
  label: string
  href?: string
  icon?: string
  description?: string
  children?: NavigationItem[]
}

interface Props {
  items: NavigationItem[]
}

defineProps<Props>()
</script>
```

**규칙:**
- 계층적 메뉴 구조 지원
- 키보드 네비게이션 자동 제공
- 접근성: `aria-expanded`, `role="menuitem"` 자동 적용
- 호버 및 포커스 상태 스타일링

### 🍞 Breadcrumb 컴포넌트

```vue
<template>
  <Breadcrumb>
    <BreadcrumbList>
      <BreadcrumbItem 
        v-for="(item, index) in items" 
        :key="item.value"
      >
        <BreadcrumbLink 
          v-if="index < items.length - 1"
          :href="item.href"
          class="cursor-pointer hover:text-foreground"
        >
          {{ item.label }}
        </BreadcrumbLink>
        <BreadcrumbPage v-else>
          {{ item.label }}
        </BreadcrumbPage>
      </BreadcrumbItem>
    </BreadcrumbList>
  </Breadcrumb>
</template>

<script setup lang="ts">
interface BreadcrumbItem {
  value: string
  label: string
  href?: string
}

interface Props {
  items: BreadcrumbItem[]
}

defineProps<Props>()
</script>
```

**규칙:**
- 마지막 항목은 링크가 아닌 텍스트로 표시
- 구분자 자동 추가
- 접근성: `aria-label`, `role="navigation"` 자동 적용

---

## 피드백 컴포넌트별 규칙

### 🍞 Toast 컴포넌트

```vue
<template>
  <div>
    <Button @click="showToast" class="cursor-pointer">
      토스트 표시
    </Button>
  </div>
</template>

<script setup lang="ts">
import { useToast } from '@/components/ui/toast/use-toast'

const { toast } = useToast()

const showToast = () => {
  toast({
    title: "성공",
    description: "작업이 완료되었습니다.",
    variant: "default",
    duration: 5000,
    action: {
      label: "실행 취소",
      onClick: () => {
        // 실행 취소 로직
      }
    }
  })
}
</script>
```

**규칙:**
- `useToast` composable 사용
- `title`과 `description`은 필수
- `variant`: `default`, `destructive` 중 선택
- `duration`으로 자동 사라짐 시간 설정
- `action`으로 추가 액션 버튼 제공
- 접근성: `role="alert"` 자동 적용

### ⚠️ Alert 컴포넌트

```vue
<template>
  <Alert :variant="variant" :class="className">
    <Icon :name="iconName" class="h-4 w-4" />
    <AlertTitle v-if="title">{{ title }}</AlertTitle>
    <AlertDescription>
      <slot>{{ description }}</slot>
    </AlertDescription>
  </Alert>
</template>

<script setup lang="ts">
interface Props {
  variant?: 'default' | 'destructive'
  title?: string
  description?: string
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  variant: 'default'
})

const iconName = computed(() => {
  return props.variant === 'destructive' ? 'alert-circle' : 'info'
})
</script>
```

**규칙:**
- `variant`에 따라 아이콘 자동 선택
- `title`은 선택적, `description`은 필수
- 접근성: `role="alert"` 자동 적용
- 색상은 의미에 맞게 사용

### 📊 Progress 컴포넌트

```vue
<template>
  <div class="space-y-2">
    <div class="flex justify-between text-sm">
      <span>{{ label }}</span>
      <span>{{ value }}%</span>
    </div>
    
    <Progress 
      :value="value" 
      :class="[
        'h-2',
        className
      ]"
      :aria-valuenow="value"
      :aria-valuemin="0"
      :aria-valuemax="100"
      :aria-label="ariaLabel"
    />
    
    <p v-if="description" class="text-xs text-muted-foreground">
      {{ description }}
    </p>
  </div>
</template>

<script setup lang="ts">
interface Props {
  value: number
  label?: string
  description?: string
  ariaLabel?: string
  className?: string
}

const props = withDefaults(defineProps<Props>(), {
  value: 0,
  ariaLabel: '진행률'
})
</script>
```

**규칙:**
- `value`는 0-100 범위
- 접근성: `aria-valuenow`, `aria-valuemin`, `aria-valuemax` 제공
- 애니메이션 효과로 부드러운 전환
- 색상은 진행률에 따라 변경 가능

---

## 데이터 표시 컴포넌트별 규칙

### 📊 Table 컴포넌트

```vue
<template>
  <div class="rounded-md border">
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead 
            v-for="column in columns" 
            :key="column.key"
            :class="[
              'cursor-pointer select-none',
              sortable ? 'hover:bg-muted/50' : ''
            ]"
            @click="handleSort(column.key)"
          >
            <div class="flex items-center gap-2">
              {{ column.label }}
              <Icon 
                v-if="sortable && sortKey === column.key"
                :name="sortDirection === 'asc' ? 'chevron-up' : 'chevron-down'"
                class="h-4 w-4"
              />
            </div>
          </TableHead>
        </TableRow>
      </TableHeader>
      
      <TableBody>
        <TableRow 
          v-for="(row, index) in sortedData" 
          :key="row.id || index"
          :class="[
            'cursor-pointer hover:bg-muted/50',
            rowClassName
          ]"
          @click="handleRowClick(row)"
        >
          <TableCell 
            v-for="column in columns" 
            :key="column.key"
          >
            <slot 
              :name="`cell-${column.key}`" 
              :row="row" 
              :value="row[column.key]"
            >
              {{ row[column.key] }}
            </slot>
          </TableCell>
        </TableRow>
      </TableBody>
    </Table>
  </div>
</template>

<script setup lang="ts">
interface Column {
  key: string
  label: string
  sortable?: boolean
}

interface Props {
  columns: Column[]
  data: Record<string, any>[]
  sortable?: boolean
  rowClassName?: string
}

const props = withDefaults(defineProps<Props>(), {
  sortable: false
})

const emit = defineEmits<{
  rowClick: [row: Record<string, any>]
  sort: [key: string, direction: 'asc' | 'desc']
}>()

const sortKey = ref<string>('')
const sortDirection = ref<'asc' | 'desc'>('asc')

const sortedData = computed(() => {
  if (!props.sortable || !sortKey.value) {
    return props.data
  }
  
  return [...props.data].sort((a, b) => {
    const aVal = a[sortKey.value]
    const bVal = b[sortKey.value]
    
    if (aVal < bVal) return sortDirection.value === 'asc' ? -1 : 1
    if (aVal > bVal) return sortDirection.value === 'asc' ? 1 : -1
    return 0
  })
})

const handleSort = (key: string) => {
  if (!props.sortable) return
  
  if (sortKey.value === key) {
    sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortKey.value = key
    sortDirection.value = 'asc'
  }
  
  emit('sort', key, sortDirection.value)
}

const handleRowClick = (row: Record<string, any>) => {
  emit('rowClick', row)
}
</script>
```

**규칙:**
- `columns` 배열로 테이블 구조 정의
- 정렬 기능은 선택적 제공
- 행 클릭 이벤트 지원
- 슬롯을 통한 셀 커스터마이징
- 접근성: `scope="col"`, `scope="row"` 자동 적용

### 🎯 Avatar 컴포넌트

```vue
<template>
  <Avatar :class="className">
    <AvatarImage 
      :src="src" 
      :alt="alt"
      @error="handleImageError"
    />
    <AvatarFallback>
      {{ fallbackText }}
    </AvatarFallback>
  </Avatar>
</template>

<script setup lang="ts">
interface Props {
  src?: string
  alt: string
  fallbackText: string
  className?: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  imageError: []
}>()

const handleImageError = () => {
  emit('imageError')
}
</script>
```

**규칙:**
- `alt` 텍스트는 접근성을 위해 필수
- `AvatarFallback`은 이미지 로드 실패 시 표시
- 원형 모양 자동 적용
- 크기는 `size` prop으로 조절

---

## 🎨 스타일링 및 테마 규칙

### CSS 변수 활용
```css
:root {
  --background: 0 0% 100%;
  --foreground: 222.2 84% 4.9%;
  --primary: 222.2 47.4% 11.2%;
  --primary-foreground: 210 40% 98%;
  --secondary: 210 40% 96%;
  --secondary-foreground: 222.2 84% 4.9%;
  --muted: 210 40% 96%;
  --muted-foreground: 215.4 16.3% 46.9%;
  --accent: 210 40% 96%;
  --accent-foreground: 222.2 84% 4.9%;
  --destructive: 0 84.2% 60.2%;
  --destructive-foreground: 210 40% 98%;
  --border: 214.3 31.8% 91.4%;
  --input: 214.3 31.8% 91.4%;
  --ring: 222.2 84% 4.9%;
  --radius: 0.5rem;
}

.dark {
  --background: 222.2 84% 4.9%;
  --foreground: 210 40% 98%;
  --primary: 210 40% 98%;
  --primary-foreground: 222.2 47.4% 11.2%;
  --secondary: 217.2 32.6% 17.5%;
  --secondary-foreground: 210 40% 98%;
  --muted: 217.2 32.6% 17.5%;
  --muted-foreground: 215 20.2% 65.1%;
  --accent: 217.2 32.6% 17.5%;
  --accent-foreground: 210 40% 98%;
  --destructive: 0 62.8% 30.6%;
  --destructive-foreground: 210 40% 98%;
  --border: 217.2 32.6% 17.5%;
  --input: 217.2 32.6% 17.5%;
  --ring: 212.7 26.8% 83.9%;
}
```

### 다크 모드 지원
```vue
<template>
  <div class="min-h-screen bg-background text-foreground">
    <!-- 컨텐츠 -->
  </div>
</template>

<script setup>
// 다크 모드 토글
const isDark = ref(false)

const toggleDarkMode = () => {
  isDark.value = !isDark.value
  document.documentElement.classList.toggle('dark', isDark.value)
}

// 시스템 테마 감지
onMounted(() => {
  const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')
  isDark.value = mediaQuery.matches
  document.documentElement.classList.toggle('dark', isDark.value)
  
  mediaQuery.addEventListener('change', (e) => {
    isDark.value = e.matches
    document.documentElement.classList.toggle('dark', isDark.value)
  })
})
</script>
```

---

## 🚀 성능 최적화 규칙

### 컴포넌트 최적화
```vue
<script setup>
// v-memo로 불필요한 리렌더링 방지
const expensiveValue = computed(() => {
  return heavyCalculation(props.data)
})

// watch 대신 watchEffect 적절히 사용
watchEffect(() => {
  if (props.isVisible) {
    // 부수 효과 실행
  }
})

// 동적 import로 코드 스플리팅
const LazyComponent = defineAsyncComponent(() => 
  import('./LazyComponent.vue')
)
</script>
```

### 번들 최적화
```typescript
// Tree-shaking을 위한 named import
import { Button, Card, Input } from '@/components/ui'

// ❌ 전체 import (번들 크기 증가)
import * as UI from '@/components/ui'
```

---

## 🧪 테스팅 규칙

### 단위 테스트
```typescript
import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import Button from '@/components/ui/Button.vue'

describe('Button', () => {
  it('renders correctly', () => {
    const wrapper = mount(Button, {
      props: {
        variant: 'default',
        text: 'Test Button'
      }
    })
    
    expect(wrapper.text()).toBe('Test Button')
    expect(wrapper.classes()).toContain('cursor-pointer')
  })
  
  it('emits click event', async () => {
    const wrapper = mount(Button, {
      props: {
        text: 'Test Button'
      }
    })
    
    await wrapper.trigger('click')
    expect(wrapper.emitted('click')).toBeTruthy()
  })
})
```

### 접근성 테스트
```typescript
import { axe, toHaveNoViolations } from 'jest-axe'

expect.extend(toHaveNoViolations)

it('should not have accessibility violations', async () => {
  const wrapper = mount(Button, {
    props: {
      text: 'Test Button'
    }
  })
  
  const results = await axe(wrapper.element)
  expect(results).toHaveNoViolations()
})
```

---

## 📱 반응형 디자인 규칙

### 모바일 우선 접근
```vue
<template>
  <div class="
    grid 
    grid-cols-1 
    md:grid-cols-2 
    lg:grid-cols-3 
    gap-4
    p-4 
    md:p-6 
    lg:p-8
  ">
    <!-- 컨텐츠 -->
  </div>
</template>
```

### 터치 친화적 디자인
```css
/* 최소 터치 타겟 크기 44px */
.touch-target {
  min-height: 44px;
  min-width: 44px;
}

/* 터치 피드백 */
.touch-feedback:active {
  transform: scale(0.98);
  transition: transform 0.1s ease;
}
```

---

이 규칙들을 따라 shadcn-vue 컴포넌트를 사용하면 일관성 있고 접근 가능한 Vue.js 애플리케이션을 구축할 수 있습니다. 각 컴포넌트는 재사용 가능하고 확장 가능하도록 설계되었으며, TypeScript와 접근성 표준을 준수합니다.
