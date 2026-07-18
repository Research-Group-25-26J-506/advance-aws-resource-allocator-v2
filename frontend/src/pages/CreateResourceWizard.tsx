import { useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import Autosuggest from "@cloudscape-design/components/autosuggest";
import Ajv, { ErrorObject } from "ajv";
import Alert from "@cloudscape-design/components/alert";
import Box from "@cloudscape-design/components/box";
import Checkbox from "@cloudscape-design/components/checkbox";
import ColumnLayout from "@cloudscape-design/components/column-layout";
import Container from "@cloudscape-design/components/container";
import ContentLayout from "@cloudscape-design/components/content-layout";
import FormField from "@cloudscape-design/components/form-field";
import Header from "@cloudscape-design/components/header";
import Input from "@cloudscape-design/components/input";
import Select from "@cloudscape-design/components/select";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Spinner from "@cloudscape-design/components/spinner";
import TagEditor, { TagEditorProps } from "@cloudscape-design/components/tag-editor";
import Textarea from "@cloudscape-design/components/textarea";
import Toggle from "@cloudscape-design/components/toggle";
import Wizard from "@cloudscape-design/components/wizard";
import { api, uuidv7 } from "../api/client";
import type { Environment, TemplateDetail } from "../api/types";
import { platformEnvironments } from "../auth/auth";

interface SchemaProperty {
  type?: string;
  title?: string;
  enum?: string[];
  default?: unknown;
  minimum?: number;
  maximum?: number;
  pattern?: string;
  "x-help"?: string;
  "x-sensitive"?: boolean;
  format?: string;
}

interface FormSchema {
  properties?: Record<string, SchemaProperty>;
  required?: string[];
}

/** Cloudscape TagEditor needs a full i18n string set; kept module-level so it isn't recreated. */
const TAG_EDITOR_I18N: TagEditorProps.I18nStrings = {
  keyHeader: "Key",
  valueHeader: "Value",
  addButton: "Add tag",
  removeButton: "Remove",
  removeButtonAriaLabel: (tag) => `Remove ${tag.key}`,
  undoButton: "Undo",
  undoPrompt: "This tag will be removed",
  loading: "Loading tags",
  keyPlaceholder: "Enter key",
  valuePlaceholder: "Enter value",
  emptyTags: "No additional tags. Mandatory tags above are always applied.",
  tooManyKeysSuggestion: "You have more keys than can be displayed",
  tooManyValuesSuggestion: "You have more values than can be displayed",
  keysSuggestionLoading: "Loading keys",
  keysSuggestionError: "Keys could not be retrieved",
  valuesSuggestionLoading: "Loading values",
  valuesSuggestionError: "Values could not be retrieved",
  emptyKeyError: "You must specify a tag key",
  maxKeyCharLengthError: "The maximum number of characters in a tag key is 128.",
  maxValueCharLengthError: "The maximum number of characters in a tag value is 256.",
  duplicateKeyError: "You must specify a unique tag key.",
  invalidKeyError: "Invalid key. Keys can only contain letters, numbers, spaces and + - = . _ : / @",
  invalidValueError: "Invalid value. Values can only contain letters, numbers, spaces and + - = . _ : / @",
  awsPrefixError: "Cannot start with aws:",
  tagLimit: (available, limit) =>
    available === limit
      ? `You can add up to ${limit} tags.`
      : available === 1
        ? "You can add up to 1 more tag."
        : `You can add up to ${available} more tags.`,
  tagLimitReached: (limit) =>
    limit === 1 ? "You have reached the limit of 1 tag." : `You have reached the limit of ${limit} tags.`,
  tagLimitExceeded: (limit) =>
    limit === 1 ? "You have exceeded the limit of 1 tag." : `You have exceeded the limit of ${limit} tags.`,
  enteredKeyLabel: (key) => `Use "${key}"`,
  enteredValueLabel: (value) => `Use "${value}"`,
};

/**
 * Dynamic Create Resource Wizard (2.04): renders any template's JSON Schema (draft-07 subset,
 * no external $ref) — one component covers every template. Ajv validates client-side; the
 * Idempotency-Key (UUIDv7) is generated once per mount so retries reuse the same key. Drafts
 * persist to sessionStorage keyed by templateId@version.
 */
export default function CreateResourceWizard({ templateOverride }: { templateOverride?: string } = {}) {
  const { templateId: templateParam } = useParams<{ templateId: string }>();
  const templateId = templateOverride ?? templateParam;
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [groupNames, setGroupNames] = useState<string[]>([]);

  const [detail, setDetail] = useState<TemplateDetail | null>(null);
  const [regions, setRegions] = useState<string[]>([]);
  const [step, setStep] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  // Basics
  const [resourceName, setResourceName] = useState("");
  const [environment, setEnvironment] = useState<Environment>("DEV");
  const [region, setRegion] = useState("us-east-1");
  const [description, setDescription] = useState("");
  // Configuration (schema-driven)
  const [config, setConfig] = useState<Record<string, unknown>>({});
  // Tags (optional, user-supplied — mandatory tags are added server-side)
  const [customTags, setCustomTags] = useState<TagEditorProps.Tag[]>([]);
  // Review
  const [confirmed, setConfirmed] = useState(false);

  const idempotencyKey = useRef(uuidv7()); // once per mount — retries reuse it (2.04 enhancement)

  const draftKey = detail ? `wizard-draft:${detail.template.id}@${detail.latestVersion}` : null;

  useEffect(() => {
    if (!templateId) return;
    api.getTemplate(templateId).then((d) => {
      setDetail(d);
      const restored = sessionStorage.getItem(`wizard-draft:${d.template.id}@${d.latestVersion}`);
      if (restored) {
        const draft = JSON.parse(restored);
        setResourceName(draft.resourceName ?? "");
        setEnvironment(draft.environment ?? "DEV");
        setRegion(draft.region ?? "us-east-1");
        setConfig(draft.config ?? {});
        setCustomTags(draft.customTags ?? []);
      } else {
        // Seed defaults from the schema
        const schema: FormSchema = JSON.parse(d.schema);
        const seeded: Record<string, unknown> = {};
        Object.entries(schema.properties ?? {}).forEach(([key, prop]) => {
          if (prop.default !== undefined) seeded[key] = prop.default;
        });
        setConfig(seeded);
      }
    }).catch((e) => setError(String(e)));
  }, [templateId]);

  useEffect(() => {
    // group preselection (?group=) + existing group suggestions for the name field
    const preset = searchParams.get("group");
    if (preset) setResourceName(preset);
    Promise.all([api.listMyRequests(100).catch(() => []), api.listGroups().catch(() => [])])
      .then(([requests, groups]) =>
        setGroupNames([...new Set([...groups.map((g) => g.name), ...requests.map((r) => r.resourceName)])]),
      )
      .catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    api.regions(environment).then((r) => {
      setRegions(r);
      if (!r.includes(region)) setRegion(r[0]);
    }).catch(() => setRegions(["us-east-1"]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [environment]);

  useEffect(() => {
    if (draftKey) {
      sessionStorage.setItem(
        draftKey,
        JSON.stringify({ resourceName, environment, region, config, customTags }),
      );
    }
  }, [draftKey, resourceName, environment, region, config, customTags]);

  const schema: FormSchema | null = useMemo(() => (detail ? JSON.parse(detail.schema) : null), [detail]);

  if (!detail || !schema) {
    return (
      <Box textAlign="center" padding="xxl">
        {error ? <Alert type="error">{error}</Alert> : <Spinner size="large" />}
      </Box>
    );
  }

  const validateConfiguration = (): boolean => {
    const ajv = new Ajv({ allErrors: true, strict: false });
    const validate = ajv.compile(JSON.parse(detail.schema));
    const valid = validate(config);
    if (valid) {
      setFieldErrors({});
      return true;
    }
    // Ajv instancePath → Cloudscape errorText, implemented once (2.04 enhancement)
    const mapped: Record<string, string> = {};
    (validate.errors ?? []).forEach((err: ErrorObject) => {
      const field = err.instancePath.replace(/^\//, "") || String(err.params?.missingProperty ?? "");
      mapped[field] = err.message ?? "invalid";
    });
    setFieldErrors(mapped);
    setError("Some configuration values are invalid — fix the highlighted fields.");
    return false;
  };

  const submit = async () => {
    if (!confirmed) {
      setError("Confirm that you understand this provisions real AWS resources before submitting.");
      return;
    }
    if (!validateConfiguration()) return;
    setSubmitting(true);
    setError(null);
    try {
      const tags = Object.fromEntries(
        customTags
          .filter((t) => !t.markedForRemoval && t.key.trim() !== "")
          .map((t) => [t.key.trim(), (t.value ?? "").trim()]),
      );
      const created = await api.createRequest(
        {
          templateId: detail.template.id,
          environment,
          region,
          resourceName,
          description: description || undefined,
          configuration: config,
          tags: Object.keys(tags).length ? tags : undefined,
        },
        idempotencyKey.current,
      );
      if (draftKey) sessionStorage.removeItem(draftKey);
      navigate(`/requests/${created.id}`);
    } catch (e) {
      setError(String(e));
      setSubmitting(false);
      // A failed attempt consumed this key server-side; the edited retry is a NEW logical
      // request and needs a fresh key (same-key/different-payload is a 422 by design).
      idempotencyKey.current = uuidv7();
    }
  };

  const renderField = (key: string, prop: SchemaProperty) => {
    const label = prop.title ?? key;
    const value = config[key];
    const setValue = (v: unknown) => setConfig((c) => ({ ...c, [key]: v }));
    const errorText = fieldErrors[key];
    const sensitive = prop["x-sensitive"] || prop.format === "password";

    if (prop.enum) {
      return (
        <FormField key={key} label={label} errorText={errorText} description={prop["x-help"]}>
          <Select
            selectedOption={value ? { value: String(value), label: String(value) } : null}
            options={prop.enum.map((o) => ({ value: o, label: o }))}
            onChange={(e) => setValue(e.detail.selectedOption.value)}
          />
        </FormField>
      );
    }
    if (prop.type === "boolean") {
      return (
        <FormField key={key} label={label} errorText={errorText} description={prop["x-help"]}>
          <Toggle checked={Boolean(value)} onChange={(e) => setValue(e.detail.checked)}>
            {label}
          </Toggle>
        </FormField>
      );
    }
    if (prop.type === "integer" || prop.type === "number") {
      return (
        <FormField
          key={key}
          label={label}
          errorText={errorText}
          description={prop["x-help"]}
          constraintText={
            prop.minimum !== undefined || prop.maximum !== undefined
              ? `Range: ${prop.minimum ?? "-∞"} to ${prop.maximum ?? "∞"}`
              : undefined
          }
        >
          <Input
            type="number"
            value={value === undefined ? "" : String(value)}
            onChange={(e) => setValue(e.detail.value === "" ? undefined : Number(e.detail.value))}
          />
        </FormField>
      );
    }
    return (
      <FormField key={key} label={label} errorText={errorText} description={prop["x-help"]}>
        <Input
          type={sensitive ? "password" : "text"}
          value={value === undefined ? "" : String(value)}
          onChange={(e) => setValue(e.detail.value === "" ? undefined : e.detail.value)}
        />
      </FormField>
    );
  };

  const reviewJson = JSON.stringify(
    Object.fromEntries(
      Object.entries(config).filter(([key]) => {
        const prop = schema.properties?.[key];
        return !(prop?.["x-sensitive"] || prop?.format === "password"); // masked fields excluded
      }),
    ),
    null,
    2,
  );

  return (
    <ContentLayout header={<Header variant="h1">Provision: {detail.template.displayName}</Header>}>
      {error && (
        <Box padding={{ bottom: "m" }}>
          <Alert type="error" dismissible onDismiss={() => setError(null)}>
            {error}
          </Alert>
        </Box>
      )}
      <Wizard
        i18nStrings={{
          stepNumberLabel: (n) => `Step ${n}`,
          collapsedStepsLabel: (n, total) => `Step ${n} of ${total}`,
          cancelButton: "Cancel",
          previousButton: "Previous",
          nextButton: "Next",
          submitButton: "Submit request",
        }}
        activeStepIndex={step}
        onNavigate={(e) => {
          if (e.detail.requestedStepIndex > 1 && step === 1 && !validateConfiguration()) return;
          setError(null);
          setStep(e.detail.requestedStepIndex);
        }}
        onCancel={() => navigate("/catalog")}
        onSubmit={submit}
        isLoadingNextStep={submitting}
        steps={[
          {
            title: "Basics",
            content: (
              <Container>
                <SpaceBetween size="m">
                  <FormField
                    label="Resource group / name"
                    description="Pick an existing group to add this resource to it, or type a new name to start one."
                    constraintText="Lowercase letters, digits, hyphens; 3–63 chars."
                  >
                    <Autosuggest
                      value={resourceName}
                      onChange={(e) => setResourceName(e.detail.value)}
                      options={groupNames.map((name) => ({ value: name }))}
                      placeholder="e.g. pastry-plus"
                      enteredTextLabel={(text) => `Start new group "${text}"`}
                    />
                  </FormField>
                  <FormField label="Environment">
                    <Select
                      selectedOption={{ value: environment, label: environment }}
                      options={platformEnvironments().map((v) => ({ value: v, label: v }))}
                      onChange={(e) => setEnvironment(e.detail.selectedOption.value as Environment)}
                    />
                  </FormField>
                  {environment === "PROD" && (
                    <Alert type="warning" header="PROD requires approval">
                      This request will wait in the approvals inbox until an approver signs off.
                    </Alert>
                  )}
                  <FormField label="Region">
                    <Select
                      selectedOption={{ value: region, label: region }}
                      options={regions.map((r) => ({ value: r, label: r }))}
                      onChange={(e) => setRegion(e.detail.selectedOption.value!)}
                    />
                  </FormField>
                  <FormField label="Description — optional">
                    <Textarea value={description} onChange={(e) => setDescription(e.detail.value)} rows={2} />
                  </FormField>
                </SpaceBetween>
              </Container>
            ),
          },
          {
            title: "Configuration",
            content: (
              <Container>
                <SpaceBetween size="m">
                  {Object.entries(schema.properties ?? {}).map(([key, prop]) => renderField(key, prop))}
                </SpaceBetween>
              </Container>
            ),
          },
          {
            title: "Tags",
            content: (
              <SpaceBetween size="l">
                <Container header={<Header variant="h2">Mandatory tags</Header>}>
                  <ColumnLayout columns={2} variant="text-grid">
                    <div>
                      <Box variant="awsui-key-label">Owner</Box>
                      <Box>derived from your login</Box>
                    </div>
                    <div>
                      <Box variant="awsui-key-label">CostCenter</Box>
                      <Box>derived from your team</Box>
                    </div>
                    <div>
                      <Box variant="awsui-key-label">Environment</Box>
                      <Box>{environment}</Box>
                    </div>
                    <div>
                      <Box variant="awsui-key-label">ManagedBy</Box>
                      <Box>Platform</Box>
                    </div>
                  </ColumnLayout>
                  <Box padding={{ top: "m" }} color="text-status-inactive">
                    Computed server-side from your identity, team, and this request (also Application,
                    PlatformRequestId, SourceCommitSha). They cannot be overridden.
                  </Box>
                </Container>
                <Container
                  header={
                    <Header
                      variant="h2"
                      description="Optional key/value tags applied to this resource. A mandatory tag with the same key always wins."
                    >
                      Additional tags
                    </Header>
                  }
                >
                  <TagEditor
                    i18nStrings={TAG_EDITOR_I18N}
                    tags={customTags}
                    tagLimit={40}
                    onChange={({ detail }) => setCustomTags([...detail.tags])}
                  />
                </Container>
              </SpaceBetween>
            ),
          },
          {
            title: "Review and submit",
            content: (
              <SpaceBetween size="m">
                <Container header={<Header>Summary</Header>}>
                  <ColumnLayout columns={2} variant="text-grid">
                    <div>
                      <Box variant="awsui-key-label">Template</Box>
                      <Box>
                        {detail.template.displayName} v{detail.latestVersion}
                      </Box>
                    </div>
                    <div>
                      <Box variant="awsui-key-label">Resource name</Box>
                      <Box>{resourceName || "—"}</Box>
                    </div>
                    <div>
                      <Box variant="awsui-key-label">Environment / Region</Box>
                      <Box>
                        {environment} / {region}
                      </Box>
                    </div>
                  </ColumnLayout>
                </Container>
                <Container header={<Header>Configuration (sensitive fields excluded)</Header>}>
                  <Textarea value={reviewJson} readOnly rows={10} />
                </Container>
                <Checkbox checked={confirmed} onChange={(e) => setConfirmed(e.detail.checked)}>
                  I understand this will provision real AWS resources
                </Checkbox>
                {!confirmed && (
                  <Box color="text-status-inactive" fontSize="body-s">
                    Tick the confirmation to enable Submit.
                  </Box>
                )}
              </SpaceBetween>
            ),
          },
        ]}
      />
    </ContentLayout>
  );
}
